package com.khovanskiy.tarantool.tt

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.khovanskiy.tarantool.TarantoolBundle
import java.nio.charset.StandardCharsets

/**
 * Проверка синтаксиса Lua-файла командой `tt check`.
 *
 * tt принимает путь только относительно своего окружения, поэтому команда
 * выполняется из корня проекта с относительным путём файла.
 */
class CheckSyntaxAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible =
            event.project != null && file != null && !file.isDirectory &&
            file.extension.equals("lua", ignoreCase = true)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val basePath = project.basePath ?: return

        val relativePath = FileUtil.getRelativePath(basePath, file.path, '/')
        if (relativePath == null || relativePath.startsWith("..")) {
            notify(
                project,
                TarantoolBundle.message("check.outside.project", file.name),
                NotificationType.WARNING,
            )
            return
        }

        object : Task.Backgroundable(project, TarantoolBundle.message("check.progress", file.name), true) {
            override fun run(indicator: ProgressIndicator) {
                check(project, basePath, relativePath, file)
            }
        }.queue()
    }

    private fun check(project: Project, basePath: String, relativePath: String, file: VirtualFile) {
        val commandLine = GeneralCommandLine(TtCli.resolve(null), "check", relativePath)
            .withWorkDirectory(basePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val output = CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MS)
        val text = (output.stdout + output.stderr).trim().lines().lastOrNull().orEmpty()

        if (!output.isTimeout && output.exitCode == 0) {
            notify(
                project,
                TarantoolBundle.message("check.success", file.name),
                NotificationType.INFORMATION,
            )
        } else {
            notify(
                project,
                TarantoolBundle.message("check.failure", file.name) + "\n" + text,
                NotificationType.ERROR,
            )
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(content, type)
            .notify(project)
    }

    private companion object {
        const val TIMEOUT_MS = 60_000
    }
}
