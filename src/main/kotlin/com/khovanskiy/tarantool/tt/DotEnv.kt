package com.khovanskiy.tarantool.tt

import java.io.File

/**
 * Переменные из `.env` в корне проекта — для команд tt, которые плагин
 * запускает сам.
 *
 * Кластерная конфигурация берёт тайны из окружения (`config.context`
 * с `from: env`), и проект держит их в `.env`, который экспортирует
 * `make start`. Инстанс, поднятый плагином без этих переменных, падает
 * на первой же строке: «no TARANTOOL_CLIENT_PASSWORD environment
 * variable». Поэтому файл читается здесь, а правило — как у dotenv:
 * настоящая переменная процесса главнее строки в файле.
 *
 * Разбор нарочно простой: `KEY=value`, необязательное `export `, кавычки
 * одинарные и двойные, комментарий после пробела у значения без кавычек.
 * Подстановки `${VAR}` не раскрываются — плагину нужны только тайны.
 */
object DotEnv {

    const val FILE_NAME = ".env"

    /** Переменные файла, которых нет в окружении процесса IDE. */
    fun load(directory: File, environment: Map<String, String> = System.getenv()): Map<String, String> {
        val file = File(directory, FILE_NAME)
        if (!file.isFile) {
            return emptyMap()
        }
        val text = runCatching { file.readText() }.getOrElse { return emptyMap() }
        return parse(text).filterKeys { it !in environment }
    }

    fun parse(text: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("export ").trim()
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }
            val match = ENTRY.matchEntire(line) ?: continue
            values[match.groupValues[1]] = unquoted(match.groupValues[2].trim())
        }
        return values
    }

    private fun unquoted(value: String): String = when {
        value.length >= 2 && value.first() == '"' && value.last() == '"' -> value.substring(1, value.length - 1)
        value.length >= 2 && value.first() == '\'' && value.last() == '\'' -> value.substring(1, value.length - 1)
        else -> value.substringBefore(" #").trim()
    }

    private val ENTRY = Regex("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$")
}
