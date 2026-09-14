package com.khovanskiy.tarantool.debugger

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
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
 * Код приложения не меняется — ни строки require('emmy_debug'), ни секции
 * в config.yaml. Загрузчик плагина подключается **ролью**, первой в списке
 * ролей инстанса (TT_ROLES перекрывает список из конфигурации): ядро
 * загружает роли до validate и apply остальных, поэтому отладчик уже
 * подключён и в применении конфигурации, и в app.file, который ядро
 * запускает после ролей само. Список настоящих ролей инстанса считает сам
 * Tarantool (emmy_roles.lua) — разбирать config.yaml в IDE ненадёжно.
 * Приложению без app.file и app.module — роли на tnt-framework — только
 * этот путь и годится.
 *
 * Запасной путь, когда инстанс не выбран и ролей не узнать: подмена
 * `app.file` загрузчиком через TT_APP_FILE — тогда настоящее приложение
 * загружает он сам, а роли к его приходу уже применены.
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

        // Панель показывает инстансы как «приложение:инстанс» — в таком виде
        // их принимает tt. Приложению же известно только короткое имя
        // (box.info.name), поэтому загрузчику передаётся оно.
        val boxName = (instance ?: singleInstance(lines))?.substringAfterLast(':')
        if (boxName == null) {
            DebugAttach.notify(project, TarantoolBundle.message("debug.error.no.instance"))
            return
        }

        val launch = DebugLaunch.prepare()

        // Без выбранного инстанса команда адресуется всему приложению:
        // короткое имя из конфигурации tt не принимает.
        val ttTarget = instance?.let { arrayOf(it) } ?: emptyArray()

        object : Task.Backgroundable(project, TarantoolBundle.message("debug.progress.starting", boxName), false) {
            override fun run(indicator: ProgressIndicator) {
                // Ролью — только для выбранного инстанса: TT_ROLES один на всех,
                // кого поднимает команда, а роли у роутера и хранилища разные.
                val roles = if (instance != null) effectiveRoles(project, launch, config, boxName, indicator) else null
                val attachment = when {
                    roles != null -> launch.roleEnvironment(roles)
                    app != null -> mapOf("TT_APP_FILE" to launch.bootstrapPath())
                    else -> {
                        launch.cleanup()
                        DebugAttach.notify(project, TarantoolBundle.message("debug.error.no.app"))
                        return
                    }
                }
                val environment = launch.environment(
                    instance = boxName,
                    // Настоящее приложение загружает сам загрузчик только
                    // на запасном пути: ролью его запускает ядро, как обычно.
                    appFile = if (roles == null) app?.file?.let { File(config.parentFile, it) } else null,
                    appModule = if (roles == null) app?.module else null,
                ) + attachment

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

    /**
     * Действующие роли инстанса — ответ самого Tarantool по config.yaml;
     * пусто, если скрипт не ответил (нет tt, старое ядро без модуля
     * конфигурации, негодный YAML) — тогда идёт запасной путь.
     */
    private fun effectiveRoles(
        project: Project,
        launch: DebugLaunch,
        config: File,
        boxName: String,
        indicator: ProgressIndicator,
    ): List<String>? {
        val commandLine = TtExecution.ttCommand(project, "run", launch.rolesScriptPath(), config.path, boxName)
        val output = runCatching { CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator, TIMEOUT_MS) }
            .getOrNull() ?: return null
        if (output.isTimeout || output.exitCode != 0) {
            LOG.warn("роли инстанса $boxName не получены: ${output.stderr.trim()}")
            return null
        }
        return DebugLaunch.parseRoles(output.stdout)
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

    private val LOG = logger<ClusterDebug>()
}
