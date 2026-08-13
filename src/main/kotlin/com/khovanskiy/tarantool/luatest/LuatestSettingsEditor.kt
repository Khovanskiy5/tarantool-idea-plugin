package com.khovanskiy.tarantool.luatest

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import javax.swing.JComponent

/** Форма редактирования конфигурации luatest. */
class LuatestSettingsEditor(private val project: Project) : SettingsEditor<LuatestRunConfiguration>() {

    private val testPathField = TextFieldWithBrowseButton()
    private val luatestPathField = TextFieldWithBrowseButton()
    private val argumentsField = RawCommandLineEditor()
    private val workingDirField = TextFieldWithBrowseButton()

    init {
        installChooser(
            testPathField,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
                .withTitle(TarantoolBundle.message("luatest.editor.choose.test.path")),
        )
        installChooser(
            luatestPathField,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle(TarantoolBundle.message("luatest.editor.choose.luatest")),
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

    override fun resetEditorFrom(configuration: LuatestRunConfiguration) {
        testPathField.text = configuration.testPath
        luatestPathField.text = configuration.luatestPath
        argumentsField.text = configuration.programArguments
        workingDirField.text = configuration.workingDirectory
    }

    override fun applyEditorTo(configuration: LuatestRunConfiguration) {
        configuration.testPath = testPathField.text.trim()
        configuration.luatestPath = luatestPathField.text.trim()
        configuration.programArguments = argumentsField.text.trim()
        configuration.workingDirectory = workingDirField.text.trim()
    }

    override fun createEditor(): JComponent = panel {
        row(TarantoolBundle.message("luatest.editor.test.path")) {
            cell(testPathField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("luatest.editor.luatest.path")) {
            cell(luatestPathField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("editor.arguments")) {
            cell(argumentsField).align(AlignX.FILL)
        }
        row(TarantoolBundle.message("editor.working.dir")) {
            cell(workingDirField).align(AlignX.FILL)
        }
    }
}
