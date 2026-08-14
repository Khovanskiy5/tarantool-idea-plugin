package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.sql.splitUrlCredentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SplitUrlCredentialsTest {

    @Test
    @DisplayName("URL без query и URL без кредов не требуют переноса")
    fun urls_without_credentials_are_ignored() {
        assertNull(splitUrlCredentials("jdbc:tarantool://localhost:3301"))
        assertNull(splitUrlCredentials("jdbc:tarantool://localhost:3301?loginTimeout=5000"))
    }

    @Test
    @DisplayName("user и password вырезаются из URL")
    fun extracts_user_and_password() {
        val split = splitUrlCredentials("jdbc:tarantool://localhost:3311?user=client&password=secret")!!
        assertEquals("jdbc:tarantool://localhost:3311", split.url)
        assertEquals("client", split.user)
        assertEquals("secret", split.password)
    }

    @Test
    @DisplayName("Только user — пароль отсутствует")
    fun extracts_user_without_password() {
        val split = splitUrlCredentials("jdbc:tarantool://localhost:3301?user=admin")!!
        assertEquals("jdbc:tarantool://localhost:3301", split.url)
        assertEquals("admin", split.user)
        assertNull(split.password)
    }

    @Test
    @DisplayName("Прочие query-параметры сохраняются в исходном порядке")
    fun keeps_other_parameters() {
        val split = splitUrlCredentials(
            "jdbc:tarantool://localhost:3301?loginTimeout=5000&user=client&queryTimeout=100&password=secret",
        )!!
        assertEquals("jdbc:tarantool://localhost:3301?loginTimeout=5000&queryTimeout=100", split.url)
        assertEquals("client", split.user)
        assertEquals("secret", split.password)
    }

    @Test
    @DisplayName("Пустой пароль извлекается как пустая строка, не как null")
    fun empty_password_is_preserved() {
        val split = splitUrlCredentials("jdbc:tarantool://localhost:3301?user=guest&password=")!!
        assertEquals("jdbc:tarantool://localhost:3301", split.url)
        assertEquals("guest", split.user)
        assertEquals("", split.password)
    }
}
