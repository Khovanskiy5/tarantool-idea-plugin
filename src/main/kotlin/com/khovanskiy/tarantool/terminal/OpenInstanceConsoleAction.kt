package com.khovanskiy.tarantool.terminal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.settings.TarantoolProjectSettings
import com.khovanskiy.tarantool.settings.TarantoolRunMode
import com.khovanskiy.tarantool.toolwindow.TarantoolDataKeys
import com.khovanskiy.tarantool.tt.TtExecution
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Открывает интерактивную консоль инстанса во встроенном Терминале.
 *
 * Именно в Терминале, а не в Run-консоли: tt connect (go-prompt) открывает
 * /dev/tty, которого у процессов Run-консоли нет — там подключение падает
 * с «device not configured». Действие живёт в модуле с зависимостью
 * от плагина Terminal и появляется на панели, только когда тот включён.
 *
 * Команда зависит от режима проекта: локально — tt connect, в Docker —
 * тот же tt connect через префикс контейнера, в Kubernetes —
 * kubectl exec -it <под> с настраиваемой командой консоли.
 *
 * Текст и иконка — в XML-регистрации (com.khovanskiy.tarantool.terminal.xml)
 * и бандле: у лениво создаваемых экшенов шаблонная презентация берётся
 * оттуда, иконка из конструктора до инстанцирования не видна.
 */
class OpenInstanceConsoleAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled =
            project != null && event.getData(TarantoolDataKeys.SELECTED_INSTANCE) != null
        if (project != null && TtExecution.mode(project) == TarantoolRunMode.KUBERNETES) {
            event.presentation.text = TarantoolBundle.message("toolwindow.action.connect.pod")
        } else {
            event.presentation.text = TarantoolBundle.message("toolwindow.action.connect")
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val instance = event.getData(TarantoolDataKeys.SELECTED_INSTANCE) ?: return

        val command = when (TtExecution.mode(project)) {
            TarantoolRunMode.KUBERNETES -> {
                val console = TarantoolProjectSettings.getInstance(project)
                    .kubernetesConsoleCommand.ifBlank { "console" }
                TtExecution.kubectlShellCommand(project, "exec -it '$instance' -- $console")
            }

            else -> TtExecution.ttShellCommand(project, "connect '$instance'")
        }

        // createShellWidget помечен устаревшим, но его замена (createNewSession
        // с командой) — internal API; из двух зол выбран стабильный устаревший.
        @Suppress("DEPRECATION")
        val widget = TerminalToolWindowManager.getInstance(project)
            .createShellWidget(project.basePath, instance, true, false)
        widget.sendCommandToExecute(command)
    }
}
