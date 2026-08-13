package com.khovanskiy.tarantool.wal

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Просмотрщик снимков и журналов Tarantool: бинарный файл открывается
 * как YAML-расшифровка, полученная от `tt cat`.
 */
class WalFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.extension?.lowercase() in TarantoolWalFileType.EXTENSIONS

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        WalPreviewFileEditor(project, file)

    override fun getEditorTypeId(): String = "tarantool-wal-preview"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
