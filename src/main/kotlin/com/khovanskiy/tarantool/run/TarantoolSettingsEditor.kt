package com.khovanskiy.tarantool.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import javax.swing.JComponent

/** Форма редактирования конфигурации запуска. */
class TarantoolSettingsEditor(private val project: Project) : SettingsEditor<TarantoolRunConfiguration>() {

    private val scriptField = TextFieldWithBrowseButton()
    private val interpreterField = TextFieldWithBrowseButton()
    private val argumentsField = RawCommandLineEditor()
    private val workingDirField = TextFieldWithBrowseButton()
    private val environmentComponent = EnvironmentVariablesComponent()
    private val luaPathCheckBox = JBCheckBox(TarantoolBundle.message("editor.augment.lua.path"))
    private val luaDebuggerCheckBox = JBCheckBox(TarantoolBundle.message("editor.lua.debugger"))
    private val emmyDebuggerCheckBox = JBCheckBox(TarantoolBundle.message("editor.emmy.debugger"))

    init {
        installChooser(
            scriptField,
            FileChooserDescriptorFactory.createSingleFileDescriptor("lua")
                .withTitle(TarantoolBundle.message("editor.choose.script")),
        )
        installChooser(
            interpreterField,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle(TarantoolBundle.message("editor.choose.interpreter")),
        )
        installChooser(
            workingDirField,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(TarantoolBundle.message("editor.choose.working.dir")),
        )
    }

    private fun installChooser(field: TextFieldWithBrowseButton, descriptor: FileChooserDescriptor) {
        field.addActionListener {
            FileChooser.chooseFile(descriptor, project, null) { file ->
                field.text = file.presentableUrl
            }
        }
    }

    override fun resetEditorFrom(configuration: TarantoolRunConfiguration) {
        scriptField.text = configuration.scriptPath
        interpreterField.text = configuration.interpreterPath
        argumentsField.text = configuration.programArguments
        workingDirField.text = configuration.workingDirectory
        environmentComponent.envData = EnvironmentVariablesData.create(
            configuration.envs,
            configuration.passParentEnvs,
        )
        luaPathCheckBox.isSelected = configuration.augmentLuaPath
        luaDebuggerCheckBox.isSelected = configuration.useLuaDebugger
        emmyDebuggerCheckBox.isSelected = configuration.useEmmyDebugger
    }

    override fun applyEditorTo(configuration: TarantoolRunConfiguration) {
        configuration.scriptPath = scriptField.text.trim()
        configuration.interpreterPath = interpreterField.text.trim()
        configuration.programArguments = argumentsField.text.trim()
        configuration.workingDirectory = workingDirField.text.trim()
        configuration.envs = environmentComponent.envData.envs
        configuration.passParentEnvs = environmentComponent.envData.isPassParentEnvs
        configuration.augmentLuaPath = luaPathCheckBox.isSelected
        configuration.useLuaDebugger = luaDebuggerCheckBox.isSelected
        configuration.useEmmyDebugger = emmyDebuggerCheckBox.isSelected
    }

    override fun createEditor(): JComponent = panel {
        row(TarantoolBundle.message("editor.script")) {
            cell(scriptField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("editor.interpreter")) {
            cell(interpreterField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("editor.arguments")) {
            cell(argumentsField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("editor.working.dir")) {
            cell(workingDirField).align(AlignX.FILL)
        }
        row {
            cell(environmentComponent).align(AlignX.FILL)
        }
        row {
            cell(luaPathCheckBox)
        }
        row {
            cell(luaDebuggerCheckBox)
        }
        row {
            cell(emmyDebuggerCheckBox)
                .comment(TarantoolBundle.message("editor.emmy.debugger.comment"))
        }
    }
}
