package com.khovanskiy.tarantool.stubs

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.run.TarantoolInterpreter
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Устанавливает описания типов API Tarantool для языкового сервера EmmyLua.
 *
 * Два слоя: встроенные курированные аннотации (bundled — включая vshard,
 * работают без установленного tarantool) и генерация интроспекцией
 * с установленного интерпретатора (generated — модули, не покрытые
 * бандлом, точно под версию сервера). Ручные уточнения пользователя живут
 * в manual и имеют приоритет над генерацией.
 */
class GenerateTarantoolStubsAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val basePath = project.basePath ?: return

        object : Task.Backgroundable(project, TarantoolBundle.message("progress.generating.stubs"), true) {
            override fun run(indicator: ProgressIndicator) {
                generate(project, basePath, indicator)
            }

            // Битый путь к интерпретатору в настройках или недоступный temp
            // бросают исключение до запуска процесса — это ошибка окружения,
            // а не плагина: уведомление вместо красного «IDE internal error».
            override fun onThrowable(error: Throwable) {
                notify(
                    project,
                    TarantoolBundle.message("notification.stubs.failure.title") + "\n" + error.message,
                    NotificationType.ERROR,
                )
            }
        }.queue()
    }

    private fun generate(project: Project, basePath: String, indicator: ProgressIndicator) {
        // Каталог ручных типов создаётся пустым: это место для правок
        // пользователя. Легаси-файлы прежних версий переименовываются.
        val manualDir = File(basePath, ".types/tarantool/manual")
        manualDir.mkdirs()
        ManualTypesMigration.notifyLeftovers(project, ManualTypesMigration.migrate(manualDir))

        // Бандл курированных аннотаций перезаписывается всегда: каталог
        // принадлежит плагину, как и generated.
        BundledAnnotations.extract(basePath, BundledAnnotations.installedPluginVersion())

        // resolve возвращает абсолютный путь, когда интерпретатор найден;
        // голое имя означает, что ни PATH, ни типовые каталоги не помогли.
        // Без интерпретатора работа не отменяется: бандл уже развёрнут,
        // пропущена только интроспекция.
        val interpreter = TarantoolInterpreter.resolve(null)
        if (!File(interpreter).isAbsolute) {
            finish(
                project,
                basePath,
                TarantoolBundle.message("notification.stubs.bundled.only"),
                NotificationType.WARNING,
            )
            return
        }

        // Скрипт извлекается из ресурсов во временный файл: интерпретатору
        // нужен путь на диске.
        val script = FileUtil.createTempFile("tarantool_gen_stubs", ".lua", true)
        javaClass.getResourceAsStream(STUB_GENERATOR_RESOURCE).use { input ->
            requireNotNull(input) { "ресурс $STUB_GENERATOR_RESOURCE не найден" }
            script.outputStream().use { input.copyTo(it) }
        }

        val commandLine = GeneralCommandLine(interpreter, script.absolutePath)
            .withWorkDirectory(basePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val output = CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator, TIMEOUT_MS)

        when {
            output.isTimeout -> notify(
                project,
                TarantoolBundle.message("notification.stubs.failure.title") + ": timeout",
                NotificationType.ERROR,
            )

            output.exitCode != 0 -> notify(
                project,
                TarantoolBundle.message("notification.stubs.failure.title") + "\n" +
                    (output.stderr.ifBlank { output.stdout }).takeLast(500),
                NotificationType.ERROR,
            )

            else -> finish(
                project,
                basePath,
                TarantoolBundle.message("notification.stubs.success.title") + "\n" + output.stdout.trim(),
                NotificationType.INFORMATION,
            )
        }
    }

    /**
     * Завершение с обновлением VFS и подключением каталогов типов
     * к EmmyLua2: отсутствующий .emmyrc.json создаётся, существующий
     * не трогается — если в нём не хватает каталогов, предлагается
     * кнопка (файл принадлежит проекту).
     */
    private fun finish(project: Project, basePath: String, message: String, type: NotificationType) {
        refreshTypesDirectory(basePath)

        var text = message
        var action: NotificationAction? = null
        val emmyrc = File(basePath, Emmyrc.FILE_NAME)
        if (!emmyrc.exists()) {
            Emmyrc.writeDefault(emmyrc)
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(emmyrc)
            text += "\n" + TarantoolBundle.message("notification.stubs.emmyrc.created")
        } else {
            val missing = Emmyrc.missingLibraries(emmyrc)
            if (missing.isNotEmpty()) {
                text += "\n" + TarantoolBundle.message(
                    "notification.stubs.emmyrc.missing",
                    missing.joinToString(", "),
                )
                action = NotificationAction.createSimpleExpiring(
                    TarantoolBundle.message("notification.stubs.emmyrc.patch"),
                ) {
                    // Список пересчитывается на момент клика: файл могли
                    // дописать вручную или кнопкой другого уведомления.
                    Emmyrc.addLibraries(emmyrc, Emmyrc.missingLibraries(emmyrc))
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(emmyrc)?.refresh(true, false)
                }
            }
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(text, type)
        action?.let(notification::addAction)
        notification.notify(project)
    }

    /** Обновляет снимок каталога типов в VFS, чтобы IDE увидела новые файлы. */
    private fun refreshTypesDirectory(basePath: String) {
        LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("$basePath/.types")
            ?.refresh(true, true)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(content, type)
            .notify(project)
    }

    private companion object {
        const val STUB_GENERATOR_RESOURCE = "/stubs/gen_stubs.lua"
        const val TIMEOUT_MS = 180_000
    }
}
