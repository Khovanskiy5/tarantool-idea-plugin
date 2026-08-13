package com.khovanskiy.tarantool.stubs

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.khovanskiy.tarantool.TarantoolBundle
import java.io.File

/**
 * При открытии tt-проекта без каталога типов предлагает сгенерировать
 * описания API Tarantool: одно уведомление с кнопкой, никакой магии
 * без спроса — генерация запускает внешний интерпретатор.
 *
 * Исключение из «никакой магии» — миграция легаси-имён ручных типов:
 * это дешёвое переименование без внешних процессов, а страдают от старых
 * имён ровно проекты, где каталог типов уже есть и уведомление с кнопкой
 * генерации не показывается.
 */
class TarantoolTypesStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return
        if (!File(basePath, "tt.yaml").isFile) {
            return
        }
        val manualDir = File(basePath, ".types/tarantool/manual")
        if (manualDir.isDirectory) {
            ManualTypesMigration.notifyLeftovers(project, ManualTypesMigration.migrate(manualDir))
        }
        if (File(basePath, ".types/tarantool").isDirectory) {
            return
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(
                TarantoolBundle.message("startup.types.suggestion"),
                NotificationType.INFORMATION,
            )
        notification.addAction(
            NotificationAction.createSimpleExpiring(TarantoolBundle.message("startup.types.generate")) {
                val action = ActionManager.getInstance().getAction("Tarantool.GenerateStubs") ?: return@createSimpleExpiring
                val context = SimpleDataContext.getProjectContext(project)
                val event = AnActionEvent.createEvent(action, context, null, "TarantoolStartup", ActionUiKind.NONE, null)
                ActionUtil.performActionDumbAwareWithCallbacks(action, event)
            },
        )
        notification.notify(project)
    }
}
