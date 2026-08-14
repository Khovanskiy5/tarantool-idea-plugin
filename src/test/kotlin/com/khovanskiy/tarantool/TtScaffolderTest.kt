package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.project.TtScaffolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TtScaffolderTest {

    @TempDir
    lateinit var dir: Path

    @Test
    @DisplayName("Окружение tt находится в каталоге и выше")
    fun finds_environment_upwards() {
        val env = dir.resolve("env")
        val nested = env.resolve("apps/deep")
        Files.createDirectories(nested)
        assertFalse(TtScaffolder.hasTtEnvironment(nested))

        Files.writeString(env.resolve("tt.yaml"), "env: {}\n")
        assertTrue(TtScaffolder.hasTtEnvironment(nested))
        assertTrue(TtScaffolder.hasTtEnvironment(env))
    }

    @Test
    @DisplayName("Вариант имени tt.yml тоже распознаётся")
    fun recognizes_yml_variant() {
        Files.writeString(dir.resolve("tt.yml"), "env: {}\n")
        assertTrue(TtScaffolder.hasTtEnvironment(dir))
    }

    @Test
    @DisplayName("Имя приложения приводится к алфавиту tt")
    fun sanitizes_app_name() {
        assertEquals("my_app", TtScaffolder.sanitizeAppName("my app"))
        assertEquals("app-1_2", TtScaffolder.sanitizeAppName("app-1_2"))
        assertEquals("app", TtScaffolder.sanitizeAppName(""))
    }
}
