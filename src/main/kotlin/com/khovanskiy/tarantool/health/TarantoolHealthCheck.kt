package com.khovanskiy.tarantool.health

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.vfs.LocalFileSystem
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.run.TarantoolInterpreter
import com.khovanskiy.tarantool.settings.TarantoolProjectSettings
import com.khovanskiy.tarantool.settings.TarantoolRunMode
import com.khovanskiy.tarantool.settings.TarantoolSettingsConfigurable
import com.khovanskiy.tarantool.stubs.BundledAnnotations
import com.khovanskiy.tarantool.stubs.Emmyrc
import com.khovanskiy.tarantool.stubs.ManualTypesMigration
import com.khovanskiy.tarantool.tt.TtCli
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * Стартовая диагностика tt-проекта.
 *
 * Автоматически (без внешних процессов, дёшево): миграция легаси-имён
 * ручных типов, разворачивание встроенных аннотаций и создание
 * .emmyrc.json, когда его ещё нет, — типы работают сразу после открытия.
 *
 * Диагностика с одной сводной нотификацией и fix-кнопками: плагин EmmyLua2
 * (не установлен / выключен / устарел), бинарники tarantool и tt (только
 * в локальном режиме — в Docker/Kubernetes они живут в контейнере),
 * несгенерированные или устаревшие типы, неподключённые каталоги типов
 * в .emmyrc.json, ручные стабы прежних версий. Показывается только при
 * реальных проблемах.
 *
 * Каждый шаг изолирован: сбой одного (read-only файловая система,
 * битый файл) логируется и не мешает остальным.
 */
class TarantoolHealthCheck : ProjectActivity {

    private class Item(
        val text: String,
        val action: NotificationAction? = null,
        val warning: Boolean = true,
    )

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return
        if (!File(basePath, "tt.yaml").isFile && !File(basePath, "tt.yml").isFile) {
            return
        }
        // Всё дальше — файлы и внешние процессы: не на Default-пуле,
        // общем с подсветкой и другими стартовыми активностями.
        withContext(Dispatchers.IO) {
            runChecks(project, basePath)
        }
    }

    private suspend fun runChecks(project: Project, basePath: String) {
        val manualDir = File(basePath, ".types/tarantool/manual")
        guarded("manual types migration") {
            if (manualDir.isDirectory) {
                ManualTypesMigration.notifyLeftovers(project, ManualTypesMigration.migrate(manualDir))
            }
        }

        guarded("bundled annotations") {
            ensureBundledAnnotations(project, basePath)
        }

        val localMode = TarantoolProjectSettings.getInstance(project).mode == TarantoolRunMode.LOCAL
        val items = mutableListOf<Item>()

        guarded("emmylua plugin check") {
            checkEmmyLuaPlugin(project, items)
        }

        val interpreter = TarantoolInterpreter.resolve(null)
        val interpreterFound = File(interpreter).isAbsolute
        // В Docker/Kubernetes-режимах инстансы живут в контейнере —
        // отсутствие локальных бинарников там штатно, предупреждать не о чем.
        if (localMode) {
            guarded("binaries check") {
                if (!interpreterFound) {
                    items += Item(TarantoolBundle.message("health.tarantool.missing"), openSettingsAction(project))
                }
                if (!File(TtCli.resolve(null)).isAbsolute) {
                    items += Item(TarantoolBundle.message("health.tt.missing"), openSettingsAction(project))
                }
            }
        }

        guarded("generated types check") {
            checkGeneratedTypes(project, basePath, interpreter, interpreterFound, items)
        }
        guarded("emmyrc check") {
            checkEmmyrc(project, basePath, items)
        }
        guarded("obsolete manual stubs check") {
            checkObsoleteManualStubs(project, basePath, manualDir, items)
        }

        if (items.isEmpty()) {
            return
        }
        val type = if (items.any(Item::warning)) NotificationType.WARNING else NotificationType.INFORMATION
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(
                TarantoolBundle.message("health.title"),
                items.joinToString("\n") { "• " + it.text },
                type,
            )
        // Обе кнопки «Открыть настройки» (tarantool и tt) — одна и та же.
        items.mapNotNull(Item::action)
            .distinctBy { it.templateText }
            .forEach(notification::addAction)
        notification.notify(project)
    }

    /** Сбой одного шага логируется и не гасит остальные проверки. */
    private inline fun guarded(step: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            LOG.warn("стартовая диагностика Tarantool, шаг «$step»", error)
        }
    }

    /**
     * Разворачивает встроенные аннотации, когда их нет или они от другой
     * версии плагина, и создаёт .emmyrc.json при отсутствии — в том числе
     * когда бандл уже актуален (файл могли удалить или не закоммитить).
     * Единственная «магия без спроса» помимо миграции: внешних процессов
     * нет, каталог принадлежит плагину.
     */
    private fun ensureBundledAnnotations(project: Project, basePath: String) {
        val version = BundledAnnotations.installedPluginVersion()
        var extracted = false
        var firstInstall = false
        if (!BundledAnnotations.isUpToDate(basePath, version)) {
            firstInstall = !File(basePath, BundledAnnotations.DIR).isDirectory
            extracted = BundledAnnotations.extract(basePath, version)
        }

        val emmyrc = File(basePath, Emmyrc.FILE_NAME)
        var emmyrcCreated = false
        if (!emmyrc.exists() && File(basePath, BundledAnnotations.DIR).isDirectory) {
            Emmyrc.writeDefault(emmyrc)
            emmyrcCreated = true
        }

        if (!extracted && !emmyrcCreated) {
            return
        }
        refresh(basePath)

        val lines = mutableListOf<String>()
        if (extracted) {
            lines += if (firstInstall) {
                TarantoolBundle.message("bundled.installed")
            } else {
                TarantoolBundle.message("bundled.updated", version)
            }
        }
        if (emmyrcCreated) {
            lines += TarantoolBundle.message("notification.stubs.emmyrc.created")
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tarantool")
            .createNotification(lines.joinToString("\n"), NotificationType.INFORMATION)
            .notify(project)
    }

    private fun checkEmmyLuaPlugin(project: Project, items: MutableList<Item>) {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId(EMMYLUA_PLUGIN_ID))
        when {
            descriptor == null -> items += Item(
                TarantoolBundle.message("health.emmylua.missing"),
                installEmmyLuaAction(project),
            )

            PluginManagerCore.isDisabled(descriptor.pluginId) -> items += Item(
                TarantoolBundle.message("health.emmylua.disabled"),
                installEmmyLuaAction(project),
            )

            !VersionNumbers.isAtLeast(descriptor.version.orEmpty(), 0, 24) -> items += Item(
                TarantoolBundle.message("health.emmylua.outdated", descriptor.version.orEmpty()),
                NotificationAction.createSimple(TarantoolBundle.message("health.emmylua.open.plugins")) {
                    // Страница Plugins ищется по id: её класс
                    // PluginManagerConfigurable помечен internal.
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        { configurable: Configurable ->
                            configurable is SearchableConfigurable &&
                                configurable.id == "preferences.pluginManager"
                        },
                        {},
                    )
                },
            )
        }
    }

    private suspend fun checkGeneratedTypes(
        project: Project,
        basePath: String,
        interpreter: String,
        interpreterFound: Boolean,
        items: MutableList<Item>,
    ) {
        // Без интерпретатора пункты о генерации бессмысленны: в локальном
        // режиме о самом tarantool сводка уже сообщает.
        if (!interpreterFound) {
            return
        }
        val generatedDir = File(basePath, ".types/tarantool/generated")
        if (!generatedDir.isDirectory) {
            items += Item(
                TarantoolBundle.message("health.types.not.generated"),
                generateTypesAction(project),
                warning = false,
            )
            return
        }
        // Маркер пишет генератор; старые генерации без маркера не трогаем —
        // предложение появится после первой перегенерации.
        val recorded = runCatching { File(generatedDir, ".tarantool-version").readText().trim() }
            .getOrNull() ?: return
        val current = currentTarantoolVersion(interpreter) ?: return
        if (recorded != current) {
            items += Item(
                TarantoolBundle.message("health.types.stale", recorded, current),
                generateTypesAction(project),
            )
        }
    }

    private fun checkEmmyrc(project: Project, basePath: String, items: MutableList<Item>) {
        val emmyrc = File(basePath, Emmyrc.FILE_NAME)
        if (!emmyrc.isFile) {
            return
        }
        val missing = Emmyrc.missingLibraries(emmyrc)
        if (missing.isEmpty()) {
            return
        }
        items += Item(
            TarantoolBundle.message("health.emmyrc.missing.libs", missing.joinToString(", ")),
            NotificationAction.createSimple(TarantoolBundle.message("health.emmyrc.patch")) {
                Emmyrc.addLibraries(emmyrc, Emmyrc.missingLibraries(emmyrc))
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(emmyrc)?.refresh(true, false)
            },
        )
    }

    /**
     * Ручные стабы, которые прежние версии плагина раскладывали по умолчанию,
     * теперь дублируют встроенные аннотации и засоряют автодополнение.
     * Удаление предлагается только для нетронутых копий: файл, совпадающий
     * с ресурсом плагина байт в байт, пользователь не редактировал.
     */
    private fun checkObsoleteManualStubs(
        project: Project,
        basePath: String,
        manualDir: File,
        items: MutableList<Item>,
    ) {
        if (!File(basePath, BundledAnnotations.DIR).isDirectory) {
            return
        }
        val obsolete = ManualTypesMigration.MANUAL_TYPE_FILES.filter { name ->
            val file = File(manualDir, name)
            file.isFile && javaClass.getResourceAsStream("/stubs/manual/$name")
                ?.use { it.readBytes() }
                ?.contentEquals(file.readBytes()) == true
        }
        if (obsolete.isEmpty()) {
            return
        }
        items += Item(
            TarantoolBundle.message("health.manual.obsolete", obsolete.joinToString(", ")),
            NotificationAction.createSimple(TarantoolBundle.message("health.manual.delete")) {
                obsolete.forEach { File(manualDir, it).delete() }
                refresh(basePath)
            },
            warning = false,
        )
    }

    private suspend fun currentTarantoolVersion(interpreter: String): String? {
        val commandLine = GeneralCommandLine(interpreter, "--version")
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        // runInterruptible: отмена корутины прерывает ожидание процесса.
        val output = runCatching {
            runInterruptible { CapturingProcessHandler(commandLine).runProcess(VERSION_TIMEOUT_MS) }
        }.getOrNull() ?: return null
        if (output.isTimeout || output.exitCode != 0) {
            return null
        }
        // Первая строка вида «Tarantool 3.8.0-0-gdce7be8» — маркер генератора
        // хранит значение _TARANTOOL, оно же последний токен этой строки.
        return output.stdout.lineSequence().firstOrNull()
            ?.trim()
            ?.substringAfterLast(' ')
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * createSimple, не Expiring: сводка может нести несколько fix-кнопок,
     * и клик по одной не должен прятать остальные.
     */
    private fun generateTypesAction(project: Project): NotificationAction =
        NotificationAction.createSimple(TarantoolBundle.message("health.types.generate")) {
            val action = ActionManager.getInstance().getAction("Tarantool.GenerateStubs")
                ?: return@createSimple
            val context = SimpleDataContext.getProjectContext(project)
            val event = AnActionEvent.createEvent(action, context, null, "TarantoolHealthCheck", ActionUiKind.NONE, null)
            ActionUtil.performAction(action, event)
        }

    private fun installEmmyLuaAction(project: Project): NotificationAction =
        NotificationAction.createSimple(TarantoolBundle.message("health.emmylua.install")) {
            installAndEnable(project, setOf(PluginId.getId(EMMYLUA_PLUGIN_ID))) {}
        }

    private fun openSettingsAction(project: Project): NotificationAction =
        NotificationAction.createSimple(TarantoolBundle.message("health.open.settings")) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, TarantoolSettingsConfigurable::class.java)
        }

    private fun refresh(basePath: String) {
        LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(basePath)
            ?.refresh(true, false)
        LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("$basePath/.types")
            ?.refresh(true, true)
    }

    private companion object {
        val LOG = logger<TarantoolHealthCheck>()
        const val EMMYLUA_PLUGIN_ID = "com.cppcxy.Intellij-EmmyLua"
        const val VERSION_TIMEOUT_MS = 10_000
    }
}
