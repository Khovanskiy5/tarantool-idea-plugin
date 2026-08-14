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

    private fun presentLibraries(library: JsonArray?): Set<String> =
        (library ?: JsonArray())
            .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            .map { normalize(it.asString) }
            .toSet()

    /** «./путь/» и «путь» — один каталог. */
    private fun normalize(path: String): String = path.removePrefix("./").trimEnd('/')

    private fun parse(file: File): JsonObject? = runCatching {
        JsonParser.parseString(file.readText()).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}
