package com.khovanskiy.tarantool.stubs

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
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
 * Генерирует описания типов API Tarantool для языкового сервера EmmyLua.
 *
 * Встроенный скрипт снимает API интроспекцией с установленного интерпретатора
 * и складывает результат в .types/tarantool/generated относительно корня
 * проекта. Каталог достаточно подключить в workspace.library файла
 * .emmyrc.json, чтобы получить автодополнение по box, fiber и другим модулям.
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
        }.queue()
    }

    private fun generate(project: Project, basePath: String, indicator: ProgressIndicator) {
        // resolve возвращает абсолютный путь, когда интерпретатор найден;
        // голое имя означает, что ни PATH, ни типовые каталоги не помогли.
        val interpreter = TarantoolInterpreter.resolve(null)
        if (!File(interpreter).isAbsolute) {
            notify(project, TarantoolBundle.message("notification.stubs.no.interpreter"), NotificationType.ERROR)
            return
        }

        // Ручные типы (сигнатуры спейсов, файберов, net.box) раскладываются
        // до запуска генератора: он исключает перекрытые вручную функции.
        // Существующие файлы не перезаписываются — их мог править пользователь.
        extractManualTypes(basePath)

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

            else -> {
                val emmyrcCreated = writeEmmyrcIfAbsent(basePath)
                refreshTypesDirectory(basePath)
                val extra = if (emmyrcCreated) {
                    "\n" + TarantoolBundle.message("notification.stubs.emmyrc.created")
                } else {
                    ""
                }
                notify(
                    project,
                    TarantoolBundle.message("notification.stubs.success.title") + "\n" +
                        output.stdout.trim() + extra,
                    NotificationType.INFORMATION,
                )
            }
        }
    }

    /**
     * Раскладывает ручные описания типов из ресурсов плагина: сигнатуры
     * спейсов, индексов, кортежей, файберов и net.box, которые интроспекция
     * снять не может. Пользовательские правки сохраняются: существующие
     * файлы не перезаписываются.
     */
    private fun extractManualTypes(basePath: String) {
        val manualDir = File(basePath, ".types/tarantool/manual")
        manualDir.mkdirs()
        for (name in MANUAL_TYPE_FILES) {
            val target = File(manualDir, name)
            if (target.exists()) {
                continue
            }
            javaClass.getResourceAsStream("/stubs/manual/$name")?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    /**
     * Языковой сервер EmmyLua2 подключает каталоги типов через
     * workspace.library в .emmyrc.json. Без него сгенерированные описания
     * не дают автодополнения, поэтому минимальный конфиг создаётся
     * автоматически. Существующий файл не трогаем: он принадлежит проекту.
     */
    private fun writeEmmyrcIfAbsent(basePath: String): Boolean {
        val emmyrc = File(basePath, ".emmyrc.json")
        if (emmyrc.exists()) {
            return false
        }
        emmyrc.writeText(
            """
            {
              "${'$'}schema": "https://raw.githubusercontent.com/EmmyLuaLs/emmylua-analyzer-rust/refs/heads/main/crates/emmylua_code_analysis/resources/schema.json",
              "runtime": {
                "version": "LuaJIT"
              },
              "workspace": {
                "library": [
                  ".types/tarantool/manual",
                  ".types/tarantool/generated"
                ],
                "ignoreDir": [
                  "var",
                  ".idea"
                ]
              }
            }

            """.trimIndent(),
        )
        return true
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
        val MANUAL_TYPE_FILES = listOf("box.lua", "fiber.lua", "net_box.lua")
    }
}
