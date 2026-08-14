package com.khovanskiy.tarantool.stubs

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Встроенные курированные EmmyLua-аннотации API Tarantool.
 *
 * Источник — набор tarantool-annotations из официального VS Code-расширения
 * (annotations/tarantool в репозитории плагина, лицензия BSD-2-Clause):
 * все встроенные модули с ручными уточнениями сигнатур плюс аннотации рока
 * vshard. В отличие от генерации интроспекцией, бандл не требует
 * установленного tarantool — типы работают сразу после открытия проекта,
 * в том числе в Docker/Kubernetes-режимах.
 *
 * Каталог принадлежит плагину и перезаписывается целиком, как generated;
 * правки пользователя живут в .types/tarantool/manual.
 */
object BundledAnnotations {

    /** Каталог бандла относительно корня проекта. */
    const val DIR = ".types/tarantool/bundled"

    /** Каталоги бандла для workspace.library в .emmyrc.json. */
    val LIBRARY_PATHS = listOf("$DIR/Library", "$DIR/Rocks")

    private const val RESOURCE = "/stubs/tarantool-annotations.zip"

    /**
     * Маркер версии плагина, развернувшего бандл: пока версия не сменилась,
     * повторное разворачивание не нужно.
     */
    private const val VERSION_MARKER = ".bundle-version"

    /** Версия установленного плагина; вне IDE (юнит-тесты) — «dev». */
    fun installedPluginVersion(): String =
        PluginManagerCore.getPlugin(PluginId.getId("com.khovanskiy.tarantool"))?.version ?: "dev"

    fun isUpToDate(basePath: String, version: String): Boolean =
        runCatching { File(basePath, "$DIR/$VERSION_MARKER").readText().trim() }.getOrNull() == version

    /**
     * Разворачивает бандл, целиком заменяя каталог, и записывает маркер
     * версии. Возвращает false, если ресурс с архивом отсутствует
     * в дистрибутиве.
     */
    @OptIn(ExperimentalPathApi::class)
    fun extract(basePath: String, version: String): Boolean {
        val stream = javaClass.getResourceAsStream(RESOURCE) ?: return false
        val dir = File(basePath, DIR)
        // kotlin.io.path.deleteRecursively не идёт по симлинкам (удаляет
        // сам линк, а не содержимое цели) — java.io-вариант вычистил бы
        // каталог, на который пользователь направил линк.
        dir.toPath().deleteRecursively()
        dir.mkdirs()
        val root = dir.toPath().normalize()
        stream.use { raw ->
            ZipInputStream(raw).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val target = File(dir, entry.name)
                    // Архив собирается нашей же сборкой, но путь всё равно
                    // проверяется: запись за пределами каталога недопустима.
                    check(target.toPath().normalize().startsWith(root)) {
                        "недопустимый путь в архиве аннотаций: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        File(dir, VERSION_MARKER).writeText(version + "\n")
        return true
    }
}
