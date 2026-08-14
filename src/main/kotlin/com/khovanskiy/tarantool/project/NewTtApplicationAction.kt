package com.khovanskiy.tarantool.project

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.tt.TtCli
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

/**
 * «Приложение Tarantool из шаблона…» в меню New: `tt create` в выбранном
 * каталоге открытого проекта — не нужно создавать новый проект ради
 * второго приложения в том же окружении tt. Если окружения ещё нет,
 * скаффолдер сначала выполнит `tt init`.
 */
class NewTtApplicationAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val targetDir = targetDirectory(event) ?: return

        if (!File(TtCli.resolve(null)).isAbsolute) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Tarantool")
                .createNotification(TarantoolBundle.message("project.generator.no.tt"), NotificationType.ERROR)
                .notify(project)
            return
        }

        val dialog = NewTtApplicationDialog(project, targetDir)
        if (dialog.showAndGet()) {
            TtScaffolder.createApp(project, targetDir, dialog.template(), dialog.appName())
        }
    }

    /** Каталог из контекста: выбранный каталог, каталог файла, иначе корень. */
    private fun targetDirectory(event: AnActionEvent): Path? {
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val dir = when {
            file == null -> null
            file.isDirectory -> file
            else -> file.parent
        }
        dir?.takeIf { it.isInLocalFileSystem }?.let { return it.toNioPath() }
        return event.project?.basePath?.let(Path::of)
    }
}

private class NewTtApplicationDialog(project: Project, private val targetDir: Path) : DialogWrapper(project) {

    private val templateCombo = ComboBox(TtScaffolder.TEMPLATES)
    private val nameField = JBTextField("app", 20)

    init {
        title = TarantoolBundle.message("newapp.dialog.title")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(TarantoolBundle.message("project.generator.template")) {
            cell(templateCombo)
        }
        row(TarantoolBundle.message("newapp.dialog.name")) {
            cell(nameField)
        }
        row {
            comment(TarantoolBundle.message("newapp.dialog.target", targetDir.toString()))
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (!name.matches(NAME_PATTERN)) {
            return ValidationInfo(TarantoolBundle.message("newapp.validation.name"), nameField)
        }
        if (Files.exists(targetDir.resolve(name))) {
            return ValidationInfo(TarantoolBundle.message("newapp.validation.exists", name), nameField)
        }
        return null
    }

    fun template(): String = templateCombo.selectedItem as String

    fun appName(): String = nameField.text.trim()

    private companion object {
        /** Требование tt к имени приложения. */
        val NAME_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
