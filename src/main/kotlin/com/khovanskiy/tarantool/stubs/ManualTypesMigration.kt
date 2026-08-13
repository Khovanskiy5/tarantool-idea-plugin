package com.khovanskiy.tarantool.stubs

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.khovanskiy.tarantool.TarantoolBundle
import java.io.File

/**
 * Миграция ручных описаний типов со старых имён на новые.
 *
 * Ранние версии плагина раскладывали ручные типы под именами модулей
 * (fiber.lua, box.lua, net_box.lua). Такой файл в library-каталоге сам
 * становится кандидатом на require('fiber'), побеждает сгенерированный
 * модуль и, не имея return, оставляет тип неизвестным — подсказки
 * пропадают. Новые имена (*_objects.lua) с модулями не совпадают.
 */
object ManualTypesMigration {

    /** Актуальные имена ручных файлов, которые раскладывает плагин. */
    val MANUAL_TYPE_FILES = listOf("box_objects.lua", "fiber_objects.lua", "net_box_objects.lua")

    private val LEGACY_NAMES = mapOf(
        "box.lua" to "box_objects.lua",
        "fiber.lua" to "fiber_objects.lua",
        "net_box.lua" to "net_box_objects.lua",
    )

    /** Новое имя для легаси-файла, если оно за ним закреплено. */
    fun targetFor(legacyName: String): String? = LEGACY_NAMES[legacyName]

    /**
     * Переименовывает легаси-файлы, сохраняя правки пользователя.
     *
     * Возвращает имена, оставшиеся под старыми именами: переименование не
     * удалось (файл занят, каталог только на чтение) или рядом уже лежит
     * файл с новым именем — затирать ни тот, ни другой нельзя. Оставшийся
     * файл продолжает перехватывать require, поэтому о нём нужно сообщить.
     */
    fun migrate(manualDir: File): List<String> {
        val leftovers = mutableListOf<String>()
        for ((legacy, current) in LEGACY_NAMES) {
            val old = File(manualDir, legacy)
            if (!old.exists()) {
                continue
            }
            val renamed = !File(manualDir, current).exists() && old.renameTo(File(manualDir, current))
            if (!renamed) {
                leftovers += legacy
            }
        }
        return leftovers
    }

    /** Предупреждает, что легаси-файлы нужно разобрать руками. */
    fun notifyLeftovers(project: Project, leftovers: List<String>) {
        if (leftovers.isEmpty()) {
            return
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(
                TarantoolBundle.message("notification.stubs.legacy.manual", leftovers.joinToString(", ")),
                NotificationType.WARNING,
            )
            .notify(project)
    }
}
