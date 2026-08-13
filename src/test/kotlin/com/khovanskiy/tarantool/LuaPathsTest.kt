package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.run.LuaPaths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LuaPathsTest {

    @Test
    @DisplayName("Корень и src добавляются перед путями по умолчанию")
    fun prepends_project_templates() {
        val path = LuaPaths.build("/proj", null)
        assertEquals("/proj/?.lua;/proj/?/init.lua;/proj/src/?.lua;/proj/src/?/init.lua;;;", path)
        // ';;' в LUA_PATH означает «вставить сюда пути по умолчанию»
        assertTrue(path.contains(";;"))
    }

    @Test
    @DisplayName("Пользовательский LUA_PATH сохраняется хвостом")
    fun keeps_existing_lua_path() {
        val path = LuaPaths.build("/proj/", "/custom/?.lua;;")
        assertEquals("/proj/?.lua;/proj/?/init.lua;/proj/src/?.lua;/proj/src/?/init.lua;/custom/?.lua;;", path)
    }

    @Test
    @DisplayName("Завершающий разделитель корня не дублируется")
    fun trims_trailing_separator() {
        val path = LuaPaths.build("/proj/", null)
        assertTrue(path.startsWith("/proj/?.lua;"))
    }
}
