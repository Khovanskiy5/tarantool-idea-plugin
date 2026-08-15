package com.khovanskiy.tarantool.tt

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import java.io.File
import javax.swing.JComponent

/** Форма редактирования конфигурации tt. */
class TtSettingsEditor(private val project: Project) : SettingsEditor<TtRunConfiguration>() {

    private val commandField = TextFieldWithAutoCompletion(
        project,
        TextFieldWithAutoCompletion.StringsCompletionProvider(completionVariants(project), null),
        false,
        "",
    )
    private val ttPathField = TextFieldWithBrowseButton()
    private val workingDirField = TextFieldWithBrowseButton()
    private val environmentComponent = EnvironmentVariablesComponent()

    init {
        installChooser(
            ttPathField,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle(TarantoolBundle.message("tt.editor.choose.tt")),
        )
        installChooser(
            workingDirField,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(TarantoolBundle.message("editor.choose.working.dir")),
        )
        commandField.setPlaceholder(TarantoolBundle.message("tt.editor.command.hint"))
    }

    /**
     * Варианты автодополнения — неинтерактивные команды tt. Интерактивный
     * connect намеренно не предлагается: ему нужен настоящий терминал
     * (/dev/tty), в Run-консоли он падает — для консоли инстанса есть
     * кнопка на панели Tarantool.
     */
    private fun completionVariants(project: Project): List<String> {
        val base = listOf(
            "start", "stop -y", "restart -y", "status",
            "check init.lua", "build", "instances", "log -f", "clean -f",
        )
        val basePath = project.basePath ?: return base
        val appName = File(basePath).name
        val instances = File(basePath, "instances.yml").takeIf { it.isFile }
            ?.readLines()
            ?.mapNotNull { line -> INSTANCE_KEY.find(line)?.groupValues?.get(1) }
            .orEmpty()
        return base + instances.map { "log -f $appName:$it" }
    }

    private fun installChooser(field: TextFieldWithBrowseButton, descriptor: FileChooserDescriptor) {
        field.addActionListener {
            FileChooser.chooseFile(descriptor, project, null) { file ->
                field.text = file.presentableUrl
            }
        }
    }

    override fun resetEditorFrom(configuration: TtRunConfiguration) {
        commandField.text = configuration.command
        ttPathField.text = configuration.ttPath
        workingDirField.text = configuration.workingDirectory
        environmentComponent.envData = EnvironmentVariablesData.create(
            configuration.envs,
            configuration.passParentEnvs,
        )
    }

    override fun applyEditorTo(configuration: TtRunConfiguration) {
        configuration.command = commandField.text.trim()
        configuration.ttPath = ttPathField.text.trim()
        configuration.workingDirectory = workingDirField.text.trim()
        configuration.envs = environmentComponent.envData.envs
        configuration.passParentEnvs = environmentComponent.envData.isPassParentEnvs
    }

    override fun createEditor(): JComponent = panel {
        row(TarantoolBundle.message("tt.editor.command")) {
            cell(commandField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("tt.editor.tt.path")) {
            cell(ttPathField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("editor.working.dir")) {
            cell(workingDirField).align(AlignX.FILL)
        }
        row {
            cell(environmentComponent).align(AlignX.FILL)
                .comment(TarantoolBundle.message("tt.editor.env.comment"))
        }
    }

    private companion object {
        val INSTANCE_KEY = Regex("^([A-Za-z0-9_-]+)\\s*:")
    }
}
