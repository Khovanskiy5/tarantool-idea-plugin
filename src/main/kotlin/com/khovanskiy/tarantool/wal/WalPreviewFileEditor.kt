package com.khovanskiy.tarantool.wal

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.tt.TtCli
import java.beans.PropertyChangeListener
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

/**
 * Read-only редактор с YAML-расшифровкой файла .snap/.xlog/.vylog.
 *
 * Содержимое загружается в фоне: файл прогоняется через `tt cat`,
 * результат подсвечивается как YAML (если YAML-плагин установлен).
 */
class WalPreviewFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val document = EditorFactory.getInstance()
        .createDocument(TarantoolBundle.message("wal.preview.loading", file.name))

    private val viewer: EditorEx = (EditorFactory.getInstance().createViewer(document, project) as EditorEx).apply {
        highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, LightVirtualFile("preview.yaml"))
        settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isIndentGuidesShown = false
        }
    }

    init {
        load()
    }

    private fun load() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val text = dump()
            ApplicationManager.getApplication().invokeLater(
                {
                    if (!viewer.isDisposed) {
                        ApplicationManager.getApplication().runWriteAction {
                            document.setText(StringUtil.convertLineSeparators(text))
                        }
                        viewer.caretModel.moveToOffset(0)
                    }
                },
                ModalityState.any(),
            )
        }
    }

    /** Возвращает YAML-расшифровку либо текст ошибки — он тоже показывается в редакторе. */
    private fun dump(): String {
        val commandLine = GeneralCommandLine(TtCli.resolve(null), "cat", file.path)
            .withWorkDirectory(project.basePath ?: file.parent.path)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val output = try {
            CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MS)
        } catch (failure: Exception) {
            return TarantoolBundle.message("notification.tt.cat.failed", file.name) + "\n" + failure.message
        }

        return when {
            output.isTimeout ->
                TarantoolBundle.message("notification.tt.cat.failed", file.name) + ": timeout"

            output.exitCode != 0 ->
                TarantoolBundle.message("notification.tt.cat.failed", file.name) + "\n" + output.stderr

            else -> limitLines(output.stdout)
        }
    }

    /** Обрезает аномально длинный вывод, чтобы не раздувать редактор. */
    private fun limitLines(text: String): String {
        val lines = text.lineSequence().take(MAX_LINES + 1).toList()
        if (lines.size <= MAX_LINES) {
            return text
        }
        return lines.take(MAX_LINES).joinToString("\n") + "\n" +
            TarantoolBundle.message("tt.cat.truncated", MAX_LINES)
    }

    override fun getComponent(): JComponent = viewer.component

    override fun getPreferredFocusedComponent(): JComponent = viewer.contentComponent

    override fun getName(): String = TarantoolBundle.message("wal.preview.editor.name")

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(viewer)
    }

    private companion object {
        const val TIMEOUT_MS = 60_000
        const val MAX_LINES = 20_000
    }
}
