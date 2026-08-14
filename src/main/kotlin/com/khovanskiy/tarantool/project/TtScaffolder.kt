package com.khovanskiy.tarantool.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.tt.TtCli
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Создание каркаса приложения Tarantool: `tt init` + `tt create` из
 * встроенного шаблона. Общая часть мастера нового проекта и генератора
 * каталогов.
 */
object TtScaffolder {

    val TEMPLATES = arrayOf(
        "single_instance",
        "cluster",
        "vshard_cluster",
        "config_storage",
    )

    private const val TIMEOUT_MS = 120_000

    /** Разворачивает шаблон в каталоге dir под модальным прогрессом. */
    fun scaffold(project: Project, dir: Path, template: String) {
        ProgressManager.getInstance().run(object : Task.Modal(
            project,
            TarantoolBundle.message("project.generator.progress"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                Files.createDirectories(dir)
                val tt = TtCli.resolve(null)
                val appName = sanitizeAppName(dir.fileName.toString())

                runTt(tt, dir, indicator, "init")
                runTt(tt, dir, indicator, "create", template, "--name", appName, "--non-interactive")
                buildIfHasRockspec(tt, dir, dir.resolve(appName), indicator)

                removeEmptyServiceDirs(dir)
                writeGitignoreIfAbsent(dir)

                refresh(dir)
            }

            override fun onSuccess() {
                notify(
                    project,
                    TarantoolBundle.message("project.generator.done", template),
                    NotificationType.INFORMATION,
                )
            }

            override fun onThrowable(error: Throwable) {
                notify(
                    project,
                    TarantoolBundle.message("project.generator.failed") + "\n" + error.message,
                    NotificationType.ERROR,
                )
            }
        })
    }

    /**
     * Создаёт приложение из шаблона в уже открытом проекте: `tt create`
     * в выбранном каталоге, при отсутствии tt-окружения — сначала `tt init`.
     * Выполняется в фоне; по успеху открывается конфигурация приложения.
     */
    fun createApp(project: Project, targetDir: Path, template: String, appName: String) {
        object : Task.Backgroundable(
            project,
            TarantoolBundle.message("newapp.progress", appName),
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val tt = TtCli.resolve(null)
                // Повторный tt init интерактивно спрашивает про перезапись
                // tt.yaml, поэтому окружение инициализируется только
                // при отсутствии — tt ищет его от каталога вверх.
                val initialized = !hasTtEnvironment(targetDir)
                if (initialized) {
                    runTt(tt, targetDir, indicator, "init")
                }
                runTt(tt, targetDir, indicator, "create", template, "--name", appName, "--non-interactive")
                buildIfHasRockspec(tt, targetDir, targetDir.resolve(appName), indicator)

                if (initialized) {
                    removeEmptyServiceDirs(targetDir)
                }
                refresh(targetDir)
            }

            override fun onSuccess() {
                notify(
                    project,
                    TarantoolBundle.message("newapp.done", appName, template),
                    NotificationType.INFORMATION,
                )
                openAppConfig(project, targetDir.resolve(appName))
            }

            override fun onThrowable(error: Throwable) {
                notify(
                    project,
                    TarantoolBundle.message("newapp.failed", appName) + "\n" + error.message,
                    NotificationType.ERROR,
                )
            }
        }.queue()
    }

    /** Есть ли окружение tt в каталоге или выше — так же ищет его сам tt. */
    fun hasTtEnvironment(dir: Path): Boolean =
        generateSequence(dir.toAbsolutePath().normalize()) { it.parent }
            .any { Files.isRegularFile(it.resolve("tt.yaml")) || Files.isRegularFile(it.resolve("tt.yml")) }

    private fun runTt(tt: String, dir: Path, indicator: ProgressIndicator, vararg args: String) {
        indicator.text = "tt " + args.joinToString(" ")
        val commandLine = GeneralCommandLine(tt, *args)
            .withWorkDirectory(dir.toFile())
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val output = CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MS)
        check(!output.isTimeout) { "tt ${args.joinToString(" ")}: timeout" }
        check(output.exitCode == 0) {
            "tt ${args.joinToString(" ")}: ${output.stderr.ifBlank { output.stdout }.takeLast(400)}"
        }
    }

    /** Имя приложения tt: латиница, цифры, подчёркивания и дефисы. */
    fun sanitizeAppName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "app" }

    /**
     * Шаблоны с rockspec (vshard_cluster) не запускаются без установки
     * зависимостей: сборка выполняется сразу.
     */
    private fun buildIfHasRockspec(tt: String, envDir: Path, appDir: Path, indicator: ProgressIndicator) {
        val hasRockspec = Files.isDirectory(appDir) &&
            Files.list(appDir).use { entries -> entries.anyMatch { it.fileName.toString().endsWith(".rockspec") } }
        if (hasRockspec) {
            runTt(tt, envDir, indicator, "build", appDir.fileName.toString())
        }
    }

    private fun refresh(dir: Path) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)?.let {
            VfsUtil.markDirtyAndRefresh(false, true, true, it)
        }
    }

    /** Открывает конфигурацию созданного приложения в редакторе. */
    private fun openAppConfig(project: Project, appDir: Path) {
        val config = listOf("config.yml", "config.yaml", "init.lua")
            .map(appDir::resolve)
            .firstOrNull(Files::isRegularFile) ?: return
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(config) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    /**
     * Убирает пустые служебные каталоги, оставшиеся после tt init: bin,
     * include, distfiles, modules, templates. Новому проекту они не нужны —
     * tt создаёт их заново, когда они действительно понадобятся
     * (tt install, tt pack), а пустыми они только шумят в дереве проекта.
     * Проверено с tt 2.14: start/stop/status работают без них.
     */
    private fun removeEmptyServiceDirs(dir: Path) {
        for (name in listOf("bin", "include", "distfiles", "modules", "templates")) {
            val candidate = dir.resolve(name)
            runCatching {
                if (Files.isDirectory(candidate) && Files.list(candidate).use { it.count() } == 0L) {
                    Files.delete(candidate)
                }
            }
        }
    }

    /** Стартовый .gitignore: артефакты инстансов и локальное окружение tt. */
    private fun writeGitignoreIfAbsent(dir: Path) {
        val gitignore = dir.resolve(".gitignore")
        if (Files.exists(gitignore)) {
            return
        }
        Files.writeString(
            gitignore,
            """
            # Данные и артефакты инстансов: снимки, WAL, логи, сокеты, pid-файлы.
            var/

            # Локальное окружение tt: бинарники, заголовки, дистрибутивы, модули.
            bin/
            include/
            distfiles/
            .rocks/

            # Настройки IDE, специфичные для рабочего места.
            .idea/workspace.xml
            .idea/usage.statistics.xml
            .idea/shelf/

            """.trimIndent(),
        )
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(content, type)
            .notify(project)
    }
}
