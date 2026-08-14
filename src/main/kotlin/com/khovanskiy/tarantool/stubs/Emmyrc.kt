package com.khovanskiy.tarantool.stubs

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Работа с .emmyrc.json — конфигурацией языкового сервера EmmyLua2.
 *
 * Плагин создаёт минимальный конфиг сам, когда его ещё нет; существующий
 * файл принадлежит проекту и правится только по явному действию
 * пользователя (кнопка в уведомлении) — через [addLibraries].
 */
object Emmyrc {

    const val FILE_NAME = ".emmyrc.json"

    /** Каталоги типов, которые должны быть подключены в workspace.library. */
    val REQUIRED_LIBRARIES = listOf(
        ".types/tarantool/manual",
        BundledAnnotations.DIR + "/Library",
        BundledAnnotations.DIR + "/Rocks",
        ".types/tarantool/generated",
    )

    /**
     * Диагностики, отключаемые в Tarantool-проектах через diagnostics.disable.
     *
     * await-in-sync: встроенные аннотации помечают fiber.sleep, net.box,
     * socket и прочие йилдящие функции как @async (они писались под LuaLS,
     * где эта проверка выключена по умолчанию), а в emmylua_ls она включена —
     * и каждый их вызов предупреждает «Async function can only be called in
     * async function». В Tarantool весь код исполняется в файберах, поэтому
     * проверка — сплошной шум. Точечно вернуть её в файле можно строкой
     * «---@diagnostic enable: await-in-sync»: файловый enable сильнее
     * конфига.
     */
    val DISABLED_DIAGNOSTICS = listOf("await-in-sync")

    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private const val SCHEMA_URL =
        "https://raw.githubusercontent.com/EmmyLuaLs/emmylua-analyzer-rust/refs/heads/main/crates/emmylua_code_analysis/resources/schema.json"

    /** Записывает конфиг по умолчанию со всеми каталогами типов. */
    fun writeDefault(file: File) {
        val root = JsonObject()
        root.addProperty("\$schema", SCHEMA_URL)
        val runtime = JsonObject()
        runtime.addProperty("version", "LuaJIT")
        root.add("runtime", runtime)
        val workspace = JsonObject()
        val library = JsonArray()
        REQUIRED_LIBRARIES.forEach(library::add)
        workspace.add("library", library)
        val ignoreDir = JsonArray()
        ignoreDir.add("var")
        ignoreDir.add(".idea")
        workspace.add("ignoreDir", ignoreDir)
        root.add("workspace", workspace)
        val diagnostics = JsonObject()
        val disable = JsonArray()
        DISABLED_DIAGNOSTICS.forEach(disable::add)
        diagnostics.add("disable", disable)
        // Проверки nil полезны, но на идиомах Tarantool (debug.getinfo,
        // box.space[...]) слишком крикливы — понижаются до hint, не глушатся.
        // Значения severity — строго error|warning|information|hint: любую
        // опечатку emmylua_ls не прощает и молча игнорирует ВЕСЬ конфиг
        // (emmylua-analyzer-rust#1119).
        val severity = JsonObject()
        severity.addProperty("need-check-nil", "hint")
        diagnostics.add("severity", severity)
        root.add("diagnostics", diagnostics)
        file.writeText(GSON.toJson(root) + "\n")
    }

    /**
     * Каталоги из [REQUIRED_LIBRARIES], не подключённые в существующем
     * файле. Для нечитаемого, синтаксически неверного или неожиданно
     * устроенного JSON (workspace не объект, library не массив) — пустой
     * список: безопасно дописать такой файл всё равно нельзя.
     */
    fun missingLibraries(file: File): List<String> {
        val root = parse(file) ?: return emptyList()
        val workspace = root.get("workspace")
        if (workspace != null && !workspace.isJsonObject) {
            return emptyList()
        }
        val library = (workspace as? JsonObject)?.get("library")
        if (library != null && !library.isJsonArray) {
            return emptyList()
        }
        val present = presentLibraries(library as? JsonArray)
        return REQUIRED_LIBRARIES.filterNot { normalize(it) in present }
    }

    /**
     * Дописывает недостающие каталоги в workspace.library, сохраняя
     * остальное содержимое (форматирование нормализуется Gson); уже
     * подключённые пути пропускаются, поэтому повторный вызов дублей
     * не создаёт. Возвращает true, если файл изменён.
     */
    fun addLibraries(file: File, libraries: List<String>): Boolean {
        if (libraries.isEmpty()) {
            return false
        }
        val root = parse(file) ?: return false
        val workspaceElement = root.get("workspace")
        val workspace = when {
            workspaceElement == null -> JsonObject().also { root.add("workspace", it) }
            workspaceElement.isJsonObject -> workspaceElement.asJsonObject
            else -> return false
        }
        val libraryElement = workspace.get("library")
        val library = when {
            libraryElement == null -> JsonArray().also { workspace.add("library", it) }
            libraryElement.isJsonArray -> libraryElement.asJsonArray
            else -> return false
        }
        val present = presentLibraries(library)
        val toAdd = libraries.filterNot { normalize(it) in present }
        if (toAdd.isEmpty()) {
            return false
        }
        toAdd.forEach(library::add)
        file.writeText(GSON.toJson(root) + "\n")
        return true
    }

    /**
     * Коды из [DISABLED_DIAGNOSTICS], не отключённые в существующем файле.
     * Как и у библиотек: нечитаемый, битый или неожиданно устроенный JSON
     * (diagnostics не объект, disable не массив) даёт пустой список —
     * безопасно дописать такой файл всё равно нельзя.
     */
    fun missingDiagnosticDisables(file: File): List<String> {
        val root = parse(file) ?: return emptyList()
        val diagnostics = root.get("diagnostics")
        if (diagnostics != null && !diagnostics.isJsonObject) {
            return emptyList()
        }
        val disable = (diagnostics as? JsonObject)?.get("disable")
        if (disable != null && !disable.isJsonArray) {
            return emptyList()
        }
        val present = presentStrings(disable as? JsonArray)
        return DISABLED_DIAGNOSTICS.filterNot { it in present }
    }

    /**
     * Дописывает недостающие коды в diagnostics.disable, сохраняя остальное
     * содержимое; повторный вызов дублей не создаёт. Возвращает true,
     * если файл изменён.
     */
    fun addDiagnosticDisables(file: File, codes: List<String>): Boolean {
        if (codes.isEmpty()) {
            return false
        }
        val root = parse(file) ?: return false
        val diagnosticsElement = root.get("diagnostics")
        val diagnostics = when {
            diagnosticsElement == null -> JsonObject().also { root.add("diagnostics", it) }
            diagnosticsElement.isJsonObject -> diagnosticsElement.asJsonObject
            else -> return false
        }
        val disableElement = diagnostics.get("disable")
        val disable = when {
            disableElement == null -> JsonArray().also { diagnostics.add("disable", it) }
            disableElement.isJsonArray -> disableElement.asJsonArray
            else -> return false
        }
        val present = presentStrings(disable)
        val toAdd = codes.filterNot { it in present }
        if (toAdd.isEmpty()) {
            return false
        }
        toAdd.forEach(disable::add)
        file.writeText(GSON.toJson(root) + "\n")
        return true
    }

    private fun presentLibraries(library: JsonArray?): Set<String> =
        presentStrings(library).map(::normalize).toSet()

    private fun presentStrings(array: JsonArray?): Set<String> =
        (array ?: JsonArray())
            .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            .map { it.asString }
            .toSet()

    /** «./путь/» и «путь» — один каталог. */
    private fun normalize(path: String): String = path.removePrefix("./").trimEnd('/')

    private fun parse(file: File): JsonObject? = runCatching {
        JsonParser.parseString(file.readText()).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}
