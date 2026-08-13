package com.khovanskiy.tarantool.run

/**
 * Составление LUA_PATH для запуска скриптов.
 *
 * Tarantool не ищет модули в рабочем каталоге, поэтому шаблоны корня проекта
 * и src/ добавляются в начало пути. Хвост ';;' означает «дописать сюда пути
 * по умолчанию» — без него потерялись бы системные каталоги модулей.
 */
object LuaPaths {

    fun build(rootDir: String, existing: String?): String {
        val root = rootDir.trimEnd('/', '\\')
        val templates = listOf(
            "$root/?.lua",
            "$root/?/init.lua",
            "$root/src/?.lua",
            "$root/src/?/init.lua",
        )
        val tail = existing?.takeIf { it.isNotBlank() } ?: ";;"
        return templates.joinToString(";") + ";" + tail
    }
}
