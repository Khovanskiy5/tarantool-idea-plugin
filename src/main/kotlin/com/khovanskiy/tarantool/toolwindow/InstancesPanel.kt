package com.khovanskiy.tarantool.toolwindow

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.Disposable
import com.intellij.util.Alarm
import java.util.concurrent.atomic.AtomicBoolean
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.debugger.ClusterDebug
import com.khovanskiy.tarantool.settings.TarantoolProjectSettings
import com.khovanskiy.tarantool.settings.TarantoolRunMode
import com.khovanskiy.tarantool.tt.TtConfigurationType
import com.khovanskiy.tarantool.tt.TtExecution
import com.khovanskiy.tarantool.tt.TtRunConfiguration

/**
 * Содержимое панели «Tarantool»: таблица инстансов и действия.
 *
 * Панель работает в трёх режимах (Settings → Tools → Tarantool → Режим
 * запуска): локально и в Docker источник состояния — tt status, в
 * Kubernetes инстансы — это поды, и панель ходит в kubectl: get pods,
 * logs -f, delete pod (контроллер пересоздаёт под — это и есть рестарт).
 */
class InstancesPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {

    /** Режим, под который построены колонки таблицы. */
    private var columnsMode: TarantoolRunMode = TtExecution.mode(project)

    private val model = ListTableModel<InstanceRow>(*buildColumns(columnsMode))

    private val table = TableView(model)

    // Автообновление: тихий опрос состояния, пока панель на экране.
    private val pollAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val refreshInFlight = AtomicBoolean(false)

    init {
        val group = DefaultActionGroup(
            action(TarantoolBundle.message("toolwindow.action.refresh"), AllIcons.Actions.Refresh) { refresh() },
            action(
                TarantoolBundle.message("toolwindow.action.start"),
                AllIcons.Actions.Execute,
                updater = { event ->
                    // В Kubernetes подами управляет контроллер — запуска нет.
                    event.presentation.isEnabled = mode() != TarantoolRunMode.KUBERNETES
                },
            ) { runTt("start") },
            action(
                TarantoolBundle.message("toolwindow.action.stop"),
                AllIcons.Actions.Suspend,
                updater = { event ->
                    event.presentation.isEnabled = mode() != TarantoolRunMode.KUBERNETES
                },
            ) { runTt("stop", "-y") },
            action(
                TarantoolBundle.message("toolwindow.action.restart"),
                AllIcons.Actions.Restart,
                updater = { event ->
                    if (mode() == TarantoolRunMode.KUBERNETES) {
                        event.presentation.text = TarantoolBundle.message("toolwindow.action.restart.pod")
                        event.presentation.isEnabled = selectedInstance() != null
                    } else {
                        event.presentation.text = TarantoolBundle.message("toolwindow.action.restart")
                    }
                },
            ) { restart() },
            action(
                TarantoolBundle.message("toolwindow.action.debug"),
                AllIcons.Actions.StartDebugger,
                updater = { event ->
                    // Отладчик и маркеры рукопожатия живут на этой машине —
                    // в Docker и Kubernetes инстанс их не увидит.
                    event.presentation.isEnabled = mode() == TarantoolRunMode.LOCAL
                },
            ) { ClusterDebug.start(project, selectedInstance()) },
            action(
                TarantoolBundle.message("toolwindow.action.logs"),
                AllIcons.Actions.ListFiles,
                requiresSelection = true,
                updater = { event ->
                    if (mode() == TarantoolRunMode.KUBERNETES) {
                        event.presentation.text = TarantoolBundle.message("toolwindow.action.logs.pod")
                    } else {
                        event.presentation.text = TarantoolBundle.message("toolwindow.action.logs")
                    }
                },
            ) { showLogs() },
        )
        // Кнопки из необязательных модулей: консоль Терминала и другие.
        // Непопапная группа разворачивается в тулбаре плоско.
        ActionManager.getInstance().getAction(EXTRA_ACTIONS_GROUP)?.let(group::add)
        val toolbar = ActionManager.getInstance().createActionToolbar("TarantoolInstances", group, false)
        toolbar.targetComponent = table
        setToolbar(toolbar.component)
        setContent(ScrollPaneFactory.createScrollPane(table))
        schedulePoll()
    }

    override fun dispose() = Unit

    private fun mode(): TarantoolRunMode = TtExecution.mode(project)

    /**
     * Цикл автообновления. Опрос идёт без Task.Backgroundable — иначе
     * каждые несколько секунд мигал бы прогресс в статус-баре. Скрытая
     * панель не опрашивается.
     */
    private fun schedulePoll() {
        pollAlarm.addRequest({
            if (isShowing) {
                refreshQuietly()
            }
            schedulePoll()
        }, POLL_INTERVAL_MS)
    }

    /** Опрос без прогресс-индикатора; перекрывающиеся запуски отбрасываются. */
    private fun refreshQuietly() {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return
        }
        try {
            val mode = mode()
            val rows = fetchRows(mode, null)
            ApplicationManager.getApplication().invokeLater(
                { updateRows(mode, rows) },
                ModalityState.any(),
            )
        } finally {
            refreshInFlight.set(false)
        }
    }

    fun refresh() {
        val mode = mode()
        val title = if (mode == TarantoolRunMode.KUBERNETES) {
            TarantoolBundle.message("toolwindow.progress.pods")
        } else {
            TarantoolBundle.message("toolwindow.progress.status")
        }
        runBackground(title) { indicator ->
            val rows = fetchRows(mode, indicator)
            ApplicationManager.getApplication().invokeLater(
                { updateRows(mode, rows) },
                ModalityState.any(),
            )
        }
    }

    /** Текущее состояние инстансов: tt status либо kubectl get pods. */
    private fun fetchRows(mode: TarantoolRunMode, indicator: ProgressIndicator?): List<InstanceRow> {
        val output = when (mode) {
            TarantoolRunMode.KUBERNETES -> {
                val selector = TarantoolProjectSettings.getInstance(project).kubernetesPodSelector.trim()
                val args = mutableListOf("get", "pods", "-o", "json")
                if (selector.isNotEmpty()) {
                    args += listOf("-l", selector)
                }
                capture(TtExecution.kubectlCommand(project, *args.toTypedArray()), indicator)
            }

            else -> capture(TtExecution.ttCommand(project, "status", "-f", "json"), indicator)
        } ?: return emptyList()
        val parser = if (mode == TarantoolRunMode.KUBERNETES) KubeStatus::parse else TtStatus::parse
        return runCatching { parser(output) }.getOrDefault(emptyList())
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[TarantoolDataKeys.SELECTED_INSTANCE] = selectedInstance()
    }

    /** Имя выбранного инстанса (пода) — цель для start/stop/restart/connect. */
    private fun selectedInstance(): String? = table.selectedObject?.name

    /**
     * Обновляет таблицу, сохраняя выделение: автообновление раз в несколько
     * секунд не должно сбрасывать выбранный инстанс. Неизменившиеся данные
     * не трогают модель вовсе. При смене режима проекта меняются и колонки.
     */
    private fun updateRows(mode: TarantoolRunMode, rows: List<InstanceRow>) {
        if (mode != columnsMode) {
            columnsMode = mode
            model.columnInfos = buildColumns(mode)
        }
        if (rows == model.items) {
            return
        }
        val selected = selectedInstance()
        model.items = rows
        val index = selected?.let { name -> rows.indexOfFirst { it.name == name } } ?: -1
        if (index >= 0) {
            table.selectionModel.setSelectionInterval(index, index)
        }
    }

    private fun runTt(command: String, vararg args: String) {
        val target = selectedInstance()
        runBackground("tt $command") { indicator ->
            val full = mutableListOf(command)
            target?.let { full += it }
            full += args
            capture(TtExecution.ttCommand(project, *full.toTypedArray()), indicator)
            followUp()
        }
    }

    private fun restart() {
        if (mode() == TarantoolRunMode.KUBERNETES) {
            deletePod()
        } else {
            runTt("restart", "-y")
        }
    }

    /** Рестарт пода: kubectl delete pod, контроллер пересоздаёт его сам. */
    private fun deletePod() {
        val pod = selectedInstance() ?: return
        runBackground("kubectl delete pod $pod") { indicator ->
            capture(TtExecution.kubectlCommand(project, "delete", "pod", pod, "--wait=false"), indicator)
            followUp()
        }
    }

    /** Обновление после действия: сразу и досылкой — состояние доезжает с задержкой. */
    private fun followUp() {
        ApplicationManager.getApplication().invokeLater({ refresh() }, ModalityState.any())
        pollAlarm.addRequest({ refreshQuietly() }, ACTION_FOLLOWUP_MS)
    }

    /**
     * Живой хвост журнала в Run-консоли. Локально и в Docker — tt log -f
     * через временную tt-конфигурацию (пути file.lua:line кликабельны),
     * в Kubernetes — kubectl logs -f выбранного пода.
     */
    private fun showLogs() {
        val target = selectedInstance()
        if (mode() == TarantoolRunMode.KUBERNETES) {
            target?.let(::showPodLogs)
            return
        }
        val command = "log -f" + (target?.let { " $it" } ?: "")
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("tt $command", TtConfigurationType.instance)
        (settings.configuration as TtRunConfiguration).command = command
        settings.isTemporary = true
        runManager.addConfiguration(settings)
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    private fun showPodLogs(pod: String) {
        val commandLine = TtExecution.kubectlCommand(project, "logs", "-f", "--tail=$LOG_TAIL_LINES", pod)
        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        RunContentExecutor(project, handler)
            .withTitle("kubectl logs $pod")
            .withActivateToolWindow(true)
            .run()
    }

    private fun capture(commandLine: GeneralCommandLine, indicator: ProgressIndicator?): String? {
        val handler = CapturingProcessHandler(commandLine)
        val output = if (indicator != null) {
            handler.runProcessWithProgressIndicator(indicator, TIMEOUT_MS)
        } else {
            handler.runProcess(TIMEOUT_MS)
        }
        return output.stdout.takeIf { !output.isTimeout && output.exitCode == 0 }
    }

    private fun runBackground(title: String, work: (ProgressIndicator) -> Unit) {
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) = work(indicator)
        }.queue()
    }

    private fun buildColumns(mode: TarantoolRunMode): Array<ColumnInfo<InstanceRow, String>> =
        if (mode == TarantoolRunMode.KUBERNETES) {
            arrayOf(
                column(TarantoolBundle.message("toolwindow.column.pod")) { it.name },
                column(TarantoolBundle.message("toolwindow.column.status")) { it.status },
                column(TarantoolBundle.message("toolwindow.column.ready")) { it.pid },
                column(TarantoolBundle.message("toolwindow.column.restarts")) { it.mode },
                column(TarantoolBundle.message("toolwindow.column.node")) { it.config },
            )
        } else {
            arrayOf(
                column(TarantoolBundle.message("toolwindow.column.instance")) { it.name },
                column(TarantoolBundle.message("toolwindow.column.status")) { it.status },
                column("PID") { it.pid },
                column(TarantoolBundle.message("toolwindow.column.mode")) { it.mode },
                column("Config") { it.config },
                column("Box") { it.box },
            )
        }

    private fun column(name: String, getter: (InstanceRow) -> String): ColumnInfo<InstanceRow, String> =
        object : ColumnInfo<InstanceRow, String>(name) {
            override fun valueOf(item: InstanceRow): String = getter(item)
        }

    private fun action(
        text: String,
        icon: javax.swing.Icon,
        requiresSelection: Boolean = false,
        updater: ((AnActionEvent) -> Unit)? = null,
        handler: () -> Unit,
    ): AnAction =
        object : AnAction(text, null, icon), DumbAware {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            override fun update(event: AnActionEvent) {
                if (requiresSelection) {
                    event.presentation.isEnabled = selectedInstance() != null
                }
                updater?.invoke(event)
            }
            override fun actionPerformed(event: AnActionEvent) = handler()
        }

    private companion object {
        const val TIMEOUT_MS = 60_000
        const val POLL_INTERVAL_MS = 5_000
        const val ACTION_FOLLOWUP_MS = 2_000
        const val LOG_TAIL_LINES = 500
        const val EXTRA_ACTIONS_GROUP = "Tarantool.InstancesExtraActions"
    }
}
