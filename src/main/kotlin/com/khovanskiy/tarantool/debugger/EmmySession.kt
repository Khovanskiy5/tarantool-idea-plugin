package com.khovanskiy.tarantool.debugger

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.khovanskiy.tarantool.TarantoolBundle
import java.util.concurrent.TimeUnit

/**
 * Запуск сессии отладки силами плагина EmmyLua2.
 *
 * Точки останова, стек и панель переменных для Lua даёт EmmyLua2 — свой
 * отладчик плагин не реализует. Не хватало только автоматики: пользователь
 * должен был вручную создать конфигурацию «Emmy Debugger(NEW)», выставить
 * порт и не забыть нажать Debug в правильный момент. Здесь всё это
 * делается за него: временная конфигурация создаётся и запускается сама,
 * ровно когда процесс открыл порт.
 *
 * Обращение к классам EmmyLua2 — через рефлексию: плагин необязателен,
 * компилироваться и работать без него мы обязаны, а нужны всего три
 * сеттера (адрес, порт, режим транспорта).
 */
object EmmySession {

    /** Доступна ли отладка: EmmyLua2 установлен и предоставляет свой тип конфигурации. */
    fun available(): Boolean = EmmyCore.pluginEnabled() && configurationType() != null

    /**
     * Создаёт временную конфигурацию подключения и запускает её кнопкой
     * Debug. Возвращает текст ошибки или null при успехе.
     *
     * @param onConnected вызывается, когда сессия действительно стартовала:
     *                    по этому событию процесс Tarantool отпускают дальше
     */
    fun start(
        project: Project,
        name: String,
        host: String,
        port: Int,
        onConnected: () -> Unit,
    ): String? {
        val type = configurationType()
            ?: return TarantoolBundle.message("debug.error.no.emmylua")
        val factory = type.configurationFactories.firstOrNull()
            ?: return TarantoolBundle.message("debug.error.no.emmylua")

        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration(name, factory)
        try {
            configure(settings.configuration, host, port)
        } catch (error: ReflectiveOperationException) {
            LOG.warn("несовместимая версия EmmyLua2: не удалось настроить конфигурацию отладки", error)
            return TarantoolBundle.message("debug.error.emmylua.incompatible")
        }

        settings.isTemporary = true
        runManager.addConfiguration(settings)

        awaitSessionStart(project, name, onConnected)
        ExecutionUtil.runConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
        return null
    }

    /**
     * Режим «Tcp (IDE connect debugger)»: слушает процесс, подключается IDE.
     * Обратный режим роняет Tarantool с LuajitError, поэтому выставляется
     * явно, а не оставляется по умолчанию.
     */
    private fun configure(configuration: RunConfiguration, host: String, port: Int) {
        val target = configuration.javaClass
        target.getMethod("setHost", String::class.java).invoke(configuration, host)
        target.getMethod("setPort", Int::class.javaPrimitiveType).invoke(configuration, port)

        val setType = target.methods.firstOrNull { it.name == "setType" && it.parameterCount == 1 }
            ?: throw NoSuchMethodException("setType")
        val transport = setType.parameterTypes[0].enumConstants
            ?.firstOrNull { (it as Enum<*>).name == TRANSPORT_IDE_CONNECTS }
            ?: throw NoSuchFieldException(TRANSPORT_IDE_CONNECTS)
        setType.invoke(configuration, transport)
    }

    /**
     * Ждёт старта именно нашей сессии. Подписка снимается по первому
     * совпадению или по таймауту — «висящих» слушателей не остаётся.
     */
    private fun awaitSessionStart(project: Project, name: String, onConnected: () -> Unit) {
        val connection = project.messageBus.connect()
        connection.subscribe(
            XDebuggerManager.TOPIC,
            object : XDebuggerManagerListener {
                override fun processStarted(debugProcess: XDebugProcess) {
                    if (debugProcess.session?.sessionName != name) {
                        return
                    }
                    connection.disconnect()
                    onConnected()
                }
            },
        )
        AppExecutorUtil.getAppScheduledExecutorService()
            .schedule({ connection.disconnect() }, SUBSCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun configurationType(): ConfigurationType? =
        ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.firstOrNull { it.id == CONFIGURATION_TYPE_ID }

    private const val CONFIGURATION_TYPE_ID = "lua.emmy.debugger"
    private const val TRANSPORT_IDE_CONNECTS = "TCP_CLIENT"
    private const val SUBSCRIPTION_TIMEOUT_SECONDS = 120L

    private val LOG = logger<EmmySession>()
}
