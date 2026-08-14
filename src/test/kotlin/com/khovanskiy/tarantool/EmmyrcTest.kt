package com.khovanskiy.tarantool

import com.google.gson.JsonParser
import com.khovanskiy.tarantool.stubs.Emmyrc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class EmmyrcTest {

    @TempDir
    lateinit var dir: Path

    private val emmyrc: File
        get() = dir.resolve(".emmyrc.json").toFile()

    @Test
    @DisplayName("Конфиг по умолчанию — валидный JSON со всеми каталогами типов")
    fun default_config_is_complete() {
        Emmyrc.writeDefault(emmyrc)
        val root = JsonParser.parseString(emmyrc.readText()).asJsonObject
        assertEquals("LuaJIT", root.getAsJsonObject("runtime")["version"].asString)
        assertTrue(Emmyrc.missingLibraries(emmyrc).isEmpty())
    }

    @Test
    @DisplayName("Недостающие каталоги находятся в существующем конфиге")
    fun detects_missing_libraries() {
        emmyrc.writeText(
            """{"workspace": {"library": [".types/tarantool/manual", ".types/tarantool/generated"]}}""",
        )
        val missing = Emmyrc.missingLibraries(emmyrc)
        assertEquals(
            listOf(".types/tarantool/bundled/Library", ".types/tarantool/bundled/Rocks"),
            missing,
        )
    }

    @Test
    @DisplayName("Каталог с завершающим слэшем считается подключённым")
    fun tolerates_trailing_slash() {
        emmyrc.writeText(
            """{"workspace": {"library": [
                ".types/tarantool/manual/",
                ".types/tarantool/bundled/Library/",
                ".types/tarantool/bundled/Rocks/",
                ".types/tarantool/generated/"
            ]}}""",
        )
        assertTrue(Emmyrc.missingLibraries(emmyrc).isEmpty())
    }

    @Test
    @DisplayName("Дописывание сохраняет прочее содержимое файла")
    fun add_preserves_other_content() {
        emmyrc.writeText(
            """{"runtime": {"version": "LuaJIT"}, "diagnostics": {"disable": ["undefined-global"]}}""",
        )
        val missing = Emmyrc.missingLibraries(emmyrc)
        assertEquals(Emmyrc.REQUIRED_LIBRARIES, missing)
        assertTrue(Emmyrc.addLibraries(emmyrc, missing))

        val root = JsonParser.parseString(emmyrc.readText()).asJsonObject
        assertEquals("LuaJIT", root.getAsJsonObject("runtime")["version"].asString)
        assertEquals(
            "undefined-global",
            root.getAsJsonObject("diagnostics").getAsJsonArray("disable")[0].asString,
        )
        assertTrue(Emmyrc.missingLibraries(emmyrc).isEmpty())
    }

    @Test
    @DisplayName("Синтаксически неверный JSON не трогается и не дописывается")
    fun malformed_json_is_left_alone() {
        emmyrc.writeText("{ workspace: [broken")
        assertTrue(Emmyrc.missingLibraries(emmyrc).isEmpty())
        assertFalse(Emmyrc.addLibraries(emmyrc, Emmyrc.REQUIRED_LIBRARIES))
        assertEquals("{ workspace: [broken", emmyrc.readText())
    }

    @Test
    @DisplayName("Неожиданные типы полей — не крэш, а отказ от правки")
    fun tolerates_wrong_field_types() {
        for (content in listOf(
            """{"workspace": []}""",
            """{"workspace": null}""",
            """{"workspace": {"library": ".types/tarantool/manual"}}""",
            """[1, 2, 3]""",
        )) {
            emmyrc.writeText(content)
            assertTrue(Emmyrc.missingLibraries(emmyrc).isEmpty(), content)
            assertFalse(Emmyrc.addLibraries(emmyrc, Emmyrc.REQUIRED_LIBRARIES), content)
            assertEquals(content, emmyrc.readText(), content)
        }
    }

    @Test
    @DisplayName("Повторное дописывание не создаёт дублей")
    fun add_is_idempotent() {
        emmyrc.writeText("""{"workspace": {"library": []}}""")
        assertTrue(Emmyrc.addLibraries(emmyrc, Emmyrc.REQUIRED_LIBRARIES))
        assertFalse(Emmyrc.addLibraries(emmyrc, Emmyrc.REQUIRED_LIBRARIES))

        val library = JsonParser.parseString(emmyrc.readText()).asJsonObject
            .getAsJsonObject("workspace").getAsJsonArray("library")
        assertEquals(Emmyrc.REQUIRED_LIBRARIES.size, library.size())
    }

    @Test
    @DisplayName("Запись с префиксом ./ считается тем же каталогом")
    fun tolerates_dot_slash_prefix() {
        emmyrc.writeText(
            """{"workspace": {"library": [
                "./.types/tarantool/manual",
                "./.types/tarantool/bundled/Library",
                "./.types/tarantool/bundled/Rocks",
                "./.types/tarantool/generated"
            ]}}""",
        )
        assertTrue(Emmyrc.missingLibraries(emmyrc).isEmpty())
    }
}
