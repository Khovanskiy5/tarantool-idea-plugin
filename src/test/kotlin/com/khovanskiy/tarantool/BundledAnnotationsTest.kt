package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.stubs.BundledAnnotations
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Тесты гоняются против настоящего zip-ресурса из сборки — заодно
 * проверяется, что задача annotationsZip действительно упаковала
 * курированные аннотации в дистрибутив.
 */
class BundledAnnotationsTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    @DisplayName("Бандл разворачивается: Library, Rocks/vshard, LICENSE, маркер версии")
    fun extracts_bundle() {
        val basePath = projectDir.toString()
        assertTrue(BundledAnnotations.extract(basePath, "test-version"))

        val bundled = File(basePath, BundledAnnotations.DIR)
        assertTrue(File(bundled, "Library/box.lua").isFile)
        assertTrue(File(bundled, "Library/fiber.lua").isFile)
        assertTrue(File(bundled, "Library/net/box.lua").isFile)
        assertTrue(File(bundled, "Rocks/vshard/init.lua").isFile)
        assertTrue(File(bundled, "LICENSE").isFile)

        assertTrue(BundledAnnotations.isUpToDate(basePath, "test-version"))
        assertFalse(BundledAnnotations.isUpToDate(basePath, "other-version"))
    }

    @Test
    @DisplayName("Повторное разворачивание заменяет каталог целиком")
    fun reextract_replaces_directory() {
        val basePath = projectDir.toString()
        BundledAnnotations.extract(basePath, "v1")
        val stray = File(basePath, BundledAnnotations.DIR + "/Library/user_edit.lua")
        stray.writeText("-- правка не в том каталоге")

        BundledAnnotations.extract(basePath, "v2")
        assertFalse(stray.exists())
        assertTrue(BundledAnnotations.isUpToDate(basePath, "v2"))
    }

    @Test
    @DisplayName("Без маркера бандл считается устаревшим")
    fun missing_marker_means_outdated() {
        assertFalse(BundledAnnotations.isUpToDate(projectDir.toString(), "any"))
    }
}
