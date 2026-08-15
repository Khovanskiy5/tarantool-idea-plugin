package com.khovanskiy.tarantool.debugger

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Один сеанс графической отладки: порт, распакованный Lua-загрузчик
 * и два файла-маркера, по которым IDE и процесс Tarantool договариваются
 * о моменте подключения.
 *
 * Почему файлы, а не проба порта: единственный порт отладчика занят
 * протоколом emmy — любое «пингующее» подключение к нему процесс примет
 * за IDE и начнёт сессию. Поэтому процесс сам сообщает о готовности
 * (listen-маркер), а IDE подтверждает подключение (ready-маркер), и до
 * этого момента загрузчик придерживает запуск приложения. Благодаря
 * такому рукопожатию точки останова работают и в стартовом коде.
 */
class DebugLaunch private constructor(
    val host: String,
    val port: Int,
    private val bootstrap: File,
    private val listenMarker: File,
    private val readyMarker: File,
    private val coreDirectory: File?,
) {

    /**
     * Переменные окружения процесса Tarantool.
     *
     * @param instance   имя инстанса, который открывает порт (кластер);
     *                   null — порт открывает любой запущенный процесс
     * @param appFile    настоящий app.file, который загрузчик выполнит после
     *                   подключения IDE (кластер)
     * @param appModule  то же для app.module
     */
    fun environment(
        instance: String? = null,
        appFile: File? = null,
        appModule: String? = null,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ): Map<String, String> = buildMap {
        put("EMMY_HOST", host)
        put("EMMY_PORT", port.toString())
        coreDirectory?.let { put("EMMY_CORE_DIR", it.path) }
        put("TARANTOOL_DEBUG_LISTEN_MARKER", listenMarker.path)
        put("TARANTOOL_DEBUG_READY_MARKER", readyMarker.path)
        put("TARANTOOL_DEBUG_TIMEOUT", timeoutSeconds.toString())
        instance?.takeIf { it.isNotBlank() }?.let { put("TARANTOOL_DEBUG_INSTANCE", it) }
        appFile?.let { put("TARANTOOL_DEBUG_APP_FILE", it.path) }
        appModule?.takeIf { it.isNotBlank() }?.let { put("TARANTOOL_DEBUG_APP_MODULE", it) }
    }

    /**
     * Чанк для `tarantool -e`: выполняется до пользовательского скрипта,
     * поэтому отладчик успевает подключиться, а сам скрипт не меняется.
     */
    fun bootstrapChunk(): String = chunkFor(bootstrap.path)

    /** Путь загрузчика для TT_APP_FILE (кластерный запуск). */
    fun bootstrapPath(): String = bootstrap.path

    /**
     * Ждёт, пока процесс откроет порт отладчика. Прекращает ожидание,
     * если процесс умер раньше.
     */
    fun awaitListening(timeoutMillis: Long, alive: () -> Boolean = { true }): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (listenMarker.isFile) {
                return true
            }
            if (!alive()) {
                return false
            }
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    /** Отпускает придержанный запуск: IDE подключилась. */
    fun markIdeConnected() {
        runCatching { readyMarker.writeText("connected") }
            .onFailure { LOG.warn("не удалось создать маркер подключения ${readyMarker.path}", it) }
    }

    /** Удаляет маркеры сеанса; загрузчик переживает перезапуски. */
    fun cleanup() {
        listenMarker.delete()
        readyMarker.delete()
        listenMarker.parentFile?.takeIf { it.name.startsWith(MARKER_PREFIX) }?.delete()
    }

    companion object {
        /** Ключ, по которому конфигурация передаёт сеанс своему раннеру. */
        val KEY: Key<DebugLaunch> = Key.create("tarantool.debugger.launch")

        /** Порт из документации и готовых конфигураций; занят — берётся свободный. */
        const val PREFERRED_PORT = 9966
        const val DEFAULT_TIMEOUT_SECONDS = 60

        /**
         * Готовит сеанс: распаковывает Lua-файлы рядом с системным
         * каталогом IDE и выбирает порт.
         */
        fun prepare(preferredPort: Int = PREFERRED_PORT): DebugLaunch {
            val home = File(PathManager.getSystemPath(), SCRIPTS_DIR)
            extract(HELPER_NAME, File(home, HELPER_NAME))
            val bootstrap = File(home, BOOTSTRAP_NAME)
            extract(BOOTSTRAP_NAME, bootstrap)

            val markers = FileUtil.createTempDirectory(MARKER_PREFIX, null, true)
            return DebugLaunch(
                host = LOCALHOST,
                port = if (portAvailable(preferredPort)) preferredPort else freePort(),
                bootstrap = bootstrap,
                listenMarker = File(markers, "listening"),
                readyMarker = File(markers, "ready"),
                coreDirectory = EmmyCore.nativeDirectory(),
            )
        }

        /**
         * Загрузчик подставляется в командную строку как чанк Lua.
         * Путь берётся в длинные скобки: так его не приходится экранировать,
         * даже если в нём кавычки или обратные слэши Windows.
         */
        fun chunkFor(path: String): String = "dofile([==[$path]==])"

        /**
         * Проверка занятости порта попыткой слушать его самим: подключаться
         * к порту отладчика нельзя — процесс примет это за подключение IDE.
         */
        fun portAvailable(port: Int): Boolean = try {
            ServerSocket(port, 1, InetAddress.getByName(LOCALHOST)).use { true }
        } catch (_: IOException) {
            false
        }

        private fun freePort(): Int =
            ServerSocket(0, 1, InetAddress.getByName(LOCALHOST)).use { it.localPort }

        /** Перезаписывает файл, только когда содержимое отличается. */
        private fun extract(resource: String, target: File) {
            val bytes = DebugLaunch::class.java.getResourceAsStream("/debug/$resource")?.use { it.readBytes() }
                ?: throw IOException("ресурс /debug/$resource не найден в плагине")
            if (target.isFile && target.readBytes().contentEquals(bytes)) {
                return
            }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }

        private const val LOCALHOST = "127.0.0.1"
        private const val SCRIPTS_DIR = "tarantool/debugger"
        private const val HELPER_NAME = "emmy_debug.lua"
        private const val BOOTSTRAP_NAME = "emmy_bootstrap.lua"
        private const val MARKER_PREFIX = "tarantool-debug"
        private const val POLL_MILLIS = 50L

        private val LOG = logger<DebugLaunch>()
    }
}
