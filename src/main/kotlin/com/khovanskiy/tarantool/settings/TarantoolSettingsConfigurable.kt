package com.khovanskiy.tarantool.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.khovanskiy.tarantool.TarantoolBundle
import javax.swing.JComponent

/** Страница Settings → Tools → Tarantool: пути к tarantool и tt. */
class TarantoolSettingsConfigurable : Configurable {

    private val tarantoolField = TextFieldWithBrowseButton()
    private val ttField = TextFieldWithBrowseButton()

    init {
        listOf(tarantoolField, ttField).forEach { field ->
            field.addActionListener {
                val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                FileChooser.chooseFile(descriptor, null, null) { file ->
                    field.text = file.presentableUrl
                }
            }
        }
    }

    override fun getDisplayName(): String = "Tarantool"

    override fun createComponent(): JComponent = panel {
        row(TarantoolBundle.message("settings.tarantool.path")) {
            cell(tarantoolField)
                .align(AlignX.FILL)
                .comment(TarantoolBundle.message("settings.path.comment"))
        }
        row(TarantoolBundle.message("settings.tt.path")) {
            cell(ttField)
                .align(AlignX.FILL)
        }
    }

    override fun isModified(): Boolean {
        val settings = TarantoolSettings.getInstance()
        return tarantoolField.text.trim() != settings.tarantoolPath ||
            ttField.text.trim() != settings.ttPath
    }

    override fun apply() {
        val settings = TarantoolSettings.getInstance()
        settings.tarantoolPath = tarantoolField.text.trim()
        settings.ttPath = ttField.text.trim()
    }

    override fun reset() {
        val settings = TarantoolSettings.getInstance()
        tarantoolField.text = settings.tarantoolPath
        ttField.text = settings.ttPath
    }
}
