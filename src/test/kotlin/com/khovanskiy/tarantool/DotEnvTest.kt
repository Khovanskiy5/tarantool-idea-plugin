package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.tt.DotEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DotEnvTest {

    @Test
    @DisplayName("разбираются пары, export, кавычки и комментарии")
    fun parses_entries_quotes_and_comments() {
        val parsed = DotEnv.parse(
            """
            # пароли
            TARANTOOL_CLIENT_PASSWORD=change-me-client
            export TT_LOG_LEVEL=info
            QUOTED="строка с # решёткой"
            SINGLE='как есть'
            COMMENTED=8081 # решётка после пробела начинает комментарий
            PASSWORD=p#ss

            not a pair
            """.trimIndent(),
        )
        assertEquals(
            mapOf(
                "TARANTOOL_CLIENT_PASSWORD" to "change-me-client",
                "TT_LOG_LEVEL" to "info",
                "QUOTED" to "строка с # решёткой",
                "SINGLE" to "как есть",
                "COMMENTED" to "8081",
                "PASSWORD" to "p#ss",
            ),
            parsed,
        )
    }

    @Test
    @DisplayName("настоящая переменная окружения главнее строки файла")
    fun real_environment_wins_over_the_file() {
        val directory = Files.createTempDirectory("dotenv").toFile()
        File(directory, DotEnv.FILE_NAME).writeText("A=file\nB=file\n")
        assertEquals(mapOf("B" to "file"), DotEnv.load(directory, mapOf("A" to "real")))
    }

    @Test
    @DisplayName("без файла переменных нет")
    fun missing_file_gives_nothing() {
        val directory = Files.createTempDirectory("dotenv").toFile()
        assertEquals(emptyMap<String, String>(), DotEnv.load(directory))
    }
}
