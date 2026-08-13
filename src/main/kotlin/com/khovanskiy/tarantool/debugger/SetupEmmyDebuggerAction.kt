package com.khovanskiy.tarantool.debugger

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.khovanskiy.tarantool.TarantoolBundle
import java.io.File

/**
 * Настраивает графическую отладку через плагин EmmyLua2:
 *
 * 1. Кладёт хелпер emmy_debug.lua в каталог приложения (рядом
 *    с config.yaml; без него — в корень проекта). Хелпер находит
 *    библиотеку emmy_core в установленной IDEA, открывает порт отладчика
 *    и включается только при переменной окружения TARANTOOL_DEBUG.
 * 2. Создаёт конфигурацию запуска «Tarantool: attach debugger» —
 *    режим «Tcp ( IDE connect debugger )», порт 9966.
 *
 * Существующие файлы не перезаписываются: их мог править пользователь.
 * Остаётся одна ручная строка в коде приложения —
 * require('emmy_debug').attach_if_requested() — потому что только
 * автор знает, в каком модуле она уместна.
 */
class SetupEmmyDebuggerAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val basePath = project.basePath ?: return

        object : Task.Backgroundable(project, TarantoolBundle.message("progress.emmy.setup"), false) {
            override fun run(indicator: ProgressIndicator) {
                setup(project, basePath)
            }
        }.queue()
    }

    private fun setup(project: Project, basePath: String) {
        val appDir = findAppDir(basePath)

        val helper = File(appDir, HELPER_NAME)
        val helperCreated = extractResourceIfAbsent("/debug/$HELPER_NAME", helper)

        val runConfiguration = File(basePath, ".idea/runConfigurations/$RUN_CONFIGURATION_NAME")
        runConfiguration.parentFile.mkdirs()
        val configurationCreated =
            extractResourceIfAbsent("/debug/$RUN_CONFIGURATION_NAME", runConfiguration)

        refresh(appDir, basePath)

        val summary = if (helperCreated || configurationCreated) {
            TarantoolBundle.message("notification.emmy.created", helper.relativeToOrSelf(File(basePath)).path)
        } else {
            TarantoolBundle.message("notification.emmy.exists")
        }
        notify(
            project,
            summary + "\n" + TarantoolBundle.message("notification.emmy.usage"),
        )
    }

    /**
     * Каталог приложения tt: config.yaml в корне проекта либо в первом
     * подкаталоге (кластерные шаблоны tt create кладут приложение туда).
     */
    private fun findAppDir(basePath: String): File {
        val root = File(basePath)
        if (File(root, CONFIG_NAME).isFile) {
            return root
        }
        val nested = root.listFiles { file: File -> file.isDirectory }
            ?.sortedBy { it.name }
            ?.firstOrNull { File(it, CONFIG_NAME).isFile }
        return nested ?: root
    }

    private fun extractResourceIfAbsent(resource: String, target: File): Boolean {
        if (target.exists()) {
            return false
        }
        javaClass.getResourceAsStream(resource)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return false
        return true
    }

    private fun refresh(appDir: File, basePath: String) {
        val fs = LocalFileSystem.getInstance()
        fs.refreshAndFindFileByPath(appDir.path)?.refresh(true, false)
        fs.refreshAndFindFileByPath("$basePath/.idea")?.refresh(true, true)
    }

    private fun notify(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(TarantoolBundle.message("notification.emmy.title"), content, NotificationType.INFORMATION)
            .notify(project)
    }

    private companion object {
        const val HELPER_NAME = "emmy_debug.lua"
        const val RUN_CONFIGURATION_NAME = "Tarantool__attach_debugger.xml"
        const val CONFIG_NAME = "config.yaml"
    }
}
