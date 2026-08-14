package com.khovanskiy.tarantool.schema

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.settings.TarantoolProjectSettings
import java.util.function.Function
import javax.swing.JComponent

/**
 * Баннер над config.yaml вне tt-окружения: файл похож на кластерную
 * конфигурацию Tarantool (гитопс-репозиторий, Helm-чарт, источник
 * для etcd) — предлагает включить схему. Ответ запоминается
 * в настройках проекта, автоматически схема к чужим config.yaml
 * не цепляется.
 */
class TarantoolConfigNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.name !in TarantoolConfigSchemaProvider.CONFIG_NAMES) {
            return null
        }
        val settings = TarantoolProjectSettings.getInstance(project)
        if (settings.isSchemaDismissed(file.url)) {
            return null
        }
        // Схема уже применяется — контекст tt или ранее данное согласие.
        if (TarantoolConfigSchemaProvider.isSchemaTarget(project, file)) {
            return null
        }
        if (file.length > MAX_FILE_SIZE) {
            return null
        }
        val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: return null
        if (!TarantoolConfigHeuristics.looksLikeClusterConfig(text)) {
            return null
        }

        return Function { _: FileEditor ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                text(TarantoolBundle.message("schema.banner.text"))
                createActionLabel(TarantoolBundle.message("schema.banner.enable")) {
                    settings.enableSchema(file.url)
                    JsonSchemaService.Impl.get(project).reset()
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
                createActionLabel(TarantoolBundle.message("schema.banner.dismiss")) {
                    settings.dismissSchema(file.url)
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }

    private companion object {
        /** Конфигурации кластера — маленькие файлы; большие не читаем. */
        const val MAX_FILE_SIZE = 512 * 1024L
    }
}
