package com.khovanskiy.tarantool.debugger

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.cluster.TarantoolClusterConfig
import com.khovanskiy.tarantool.settings.TarantoolRunMode
import com.khovanskiy.tarantool.tt.TtExecution
import java.io.File

/**
 * Запуск инстанса кластера под отладчиком — одной кнопкой на панели.
 *
 * Что здесь происходит вместо прежнего ручного обряда: плагин подменяет
 * `app.file` собственным загрузчиком через переменную окружения
 * TT_APP_FILE, поэтому код приложения не нужно править вообще — ни строки
 * require('emmy_debug'), ни секции в config.yaml. Загрузчик открывает порт
 * отладчика, дожидается подключения IDE и только после этого выполняет
 * настоящее приложение: точки останова срабатывают уже в стартовом коде.
 */
object ClusterDebug {

    /**
     * @param instance имя инстанса, который откроет порт; null допустим,
     *                 когда в конфигурации ровно один инстанс
     */
    fun start(project: Project, instance: String?) {
        val basePath = project.basePath ?: return

        if (TtExecution.mode(project) != TarantoolRunMode.LOCAL) {
            DebugAttach.notify(project, TarantoolBundle.message("debug.error.mode"))
            return
        }
        if (!EmmySession.available()) {
            DebugAttach.notify(project, TarantoolBundle.message("debug.error.no.emmylua"))
            return
        }

        val config = TarantoolClusterConfig.locate(File(basePath))
        if (config == null) {
            DebugAttach.notify(project, TarantoolBundle.message("debug.error.no.config"))
            return
        }
        val lines = runCatching { config.readLines() }.getOrDefault(emptyList())
        val app = TarantoolClusterConfig.parseApp(lines)
        if (app == null) {
            DebugAttach.notify(project, TarantoolBundle.message("debug.error.no.app"))
            return
        }

        // Панель показывает инстансы как «приложение:инстанс» — в таком виде
        // их принимает tt. Приложению же известно только короткое имя
        // (box.info.name), поэтому загрузчику передаётся оно.
        val boxName = (instance ?: singleInstance(lines))?.substringAfterLast(':')
        if (boxName == null) {
            DebugAttach.notify(project, TarantoolBundle.message("debug.error.no.instance"))
            return
        }

        val launch = DebugLaunch.prepare()
        val environment = launch.environment(
            instance = boxName,
            // app.file задан относительно каталога конфигурации
            appFile = app.file?.let { File(config.parentFile, it) },
            appModule = app.module,
        ) + mapOf("TT_APP_FILE" to launch.bootstrapPath())

        // Без выбранного инстанса команда адресуется всему приложению:
        // короткое имя из конфигурации tt не принимает.
        val ttTarget = instance?.let { arrayOf(it) } ?: emptyArray()

        object : Task.Backgroundable(project, TarantoolBundle.message("debug.progress.starting", boxName), false) {
            override fun run(indicator: ProgressIndicator) {
                // Инстанс перезапускается: переменные окружения читаются
                // только при старте процесса.
                run(project, environment, indicator, "stop", *ttTarget, "-y")
                val started = run(project, environment, indicator, "start", *ttTarget)
                if (!started) {
                    launch.cleanup()
                    DebugAttach.notify(project, TarantoolBundle.message("debug.error.start.failed", boxName))
                    return
                }
                DebugAttach.notify(
                    project,
                    TarantoolBundle.message("debug.cluster.started", boxName, launch.port),
                    NotificationType.INFORMATION,
                )
            }
        }.queue()

        DebugAttach.whenListening(
            project = project,
            launch = launch,
            sessionName = TarantoolBundle.message("debug.session.name", boxName),
        )
    }

    /** Единственный инстанс конфигурации — тогда выбирать в панели нечего. */
    private fun singleInstance(lines: List<String>): String? =
        TarantoolClusterConfig.parseNodes(lines).singleOrNull()?.name

    private fun run(
        project: Project,
        environment: Map<String, String>,
        indicator: ProgressIndicator,
        vararg args: String,
    ): Boolean {
        val commandLine = TtExecution.ttCommand(project, *args).withEnvironment(environment)
        val output = CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator, TIMEOUT_MS)
        return !output.isTimeout && output.exitCode == 0
    }

    private const val TIMEOUT_MS = 60_000
}
