package com.khovanskiy.tarantool

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Границы content-модулей Plugin Model v2.
 *
 * Каждый content-модуль (json, sql, lua, terminal) грузится отдельным
 * загрузчиком классов, и основной модуль обращаться к его классам не может:
 * платформа роняет обращение в рантайме — «must not be requested from main
 * classloader», причём компилятор такое пропускает, потому что исходники
 * у всех модулей общие. Один раз уже наступили: код отладчика читал
 * config.yaml классом из модуля sql.
 *
 * Тест проверяет направление зависимостей по исходникам: модуль может
 * обращаться к основному коду, основной код к модулям — нет.
 */
class ModuleBoundariesTest {

    @Test
    @DisplayName("основной модуль не импортирует классы content-модулей")
    fun main_module_does_not_touch_content_modules() {
        val root = projectRoot()
        val prefixes = contentModulePrefixes(File(root, PLUGIN_XML))
        assertTrue(prefixes.isNotEmpty(), "в plugin.xml не найдено ни одного content-модуля")

        val violations = mutableListOf<String>()
        for (source in sourceFiles(root)) {
            val text = source.readText()
            val packageName = PACKAGE.find(text)?.groupValues?.get(1) ?: continue
            if (prefixes.any { packageName == it || packageName.startsWith("$it.") }) {
                continue // это и есть content-модуль: ему можно всё
            }
            IMPORT.findAll(text)
                .map { it.groupValues[1] }
                .filter { imported -> prefixes.any { imported.startsWith("$it.") } }
                .forEach { imported ->
                    violations += "${source.relativeTo(root).path}: import $imported"
                }
        }

        assertTrue(
            violations.isEmpty(),
            "основной модуль обращается к классам content-модулей — в рантайме это PluginException:\n" +
                violations.joinToString("\n"),
        )
    }

    /** Имена content-модулей из манифеста совпадают с их package-префиксами. */
    private fun contentModulePrefixes(pluginXml: File): List<String> =
        MODULE.findAll(pluginXml.readText()).map { it.groupValues[1] }.toList()

    private fun sourceFiles(root: File): List<File> =
        listOf("src/main/kotlin", "src/main/java")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS }.toList() }

    /** Каталог проекта: тесты могут запускаться с разным рабочим каталогом. */
    private fun projectRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile) {
                return candidate
            }
            candidate = candidate.parentFile
        }
        throw IllegalStateException("не найден корень проекта (settings.gradle.kts)")
    }

    private companion object {
        const val PLUGIN_XML = "src/main/resources/META-INF/plugin.xml"
        val SOURCE_EXTENSIONS = setOf("kt", "java")
        val PACKAGE = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
        val IMPORT = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE)
        val MODULE = Regex("""<module\s+name="([\w.]+)"\s*/>""")
    }
}
