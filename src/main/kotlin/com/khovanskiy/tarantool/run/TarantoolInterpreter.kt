package com.khovanskiy.tarantool.run

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.khovanskiy.tarantool.settings.TarantoolSettings
import java.io.File

/** Поиск исполняемого файла tarantool. */
object TarantoolInterpreter {

    const val DEFAULT_NAME = "tarantool"

    /** Типовые расположения на случай, когда PATH внутри IDE обеднён. */
    private val WELL_KNOWN_LOCATIONS = listOf(
        "/opt/homebrew/bin/tarantool",
        "/usr/local/bin/tarantool",
        "/usr/bin/tarantool",
    )

    /**
     * Возвращает путь к интерпретатору. Явно заданное значение уважается
     * как есть, затем настройки плагина (Settings → Tools → Tarantool),
     * затем PATH и известные каталоги установки.
     */
    fun resolve(configured: String?): String {
        val value = configured?.trim().orEmpty()
        if (value.isNotEmpty() && value != DEFAULT_NAME) {
            return value
        }
        TarantoolSettings.getInstance().tarantoolPath.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        PathEnvironmentVariableUtil.findInPath(DEFAULT_NAME)?.let { return it.absolutePath }
        WELL_KNOWN_LOCATIONS.firstOrNull { File(it).canExecute() }?.let { return it }
        return DEFAULT_NAME
    }
}
