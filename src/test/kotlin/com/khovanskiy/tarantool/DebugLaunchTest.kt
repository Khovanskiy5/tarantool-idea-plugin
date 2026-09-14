package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.debugger.DebugLaunch
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket

class DebugLaunchTest {

    @Test
    @DisplayName("путь загрузчика оборачивается в длинные скобки Lua")
    fun wraps_bootstrap_path_into_long_brackets() {
        assertEquals(
            "dofile([==[/Users/dev/Library/Application Support/emmy_bootstrap.lua]==])",
            DebugLaunch.chunkFor("/Users/dev/Library/Application Support/emmy_bootstrap.lua"),
        )
    }

    @Test
    @DisplayName("путь Windows с обратными слэшами не требует экранирования")
    fun windows_path_needs_no_escaping() {
        val chunk = DebugLaunch.chunkFor("""C:\Users\dev\emmy_bootstrap.lua""")
        assertEquals("""dofile([==[C:\Users\dev\emmy_bootstrap.lua]==])""", chunk)
    }

    @Test
    @DisplayName("занятый порт распознаётся без подключения к нему")
    fun detects_busy_port() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            assertFalse(DebugLaunch.portAvailable(socket.localPort))
        }
    }

    @Test
    @DisplayName("свободный порт распознаётся как свободный")
    fun detects_free_port() {
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertTrue(DebugLaunch.portAvailable(port))
    }

    @Test
    @DisplayName("роли инстанса читаются из последней строки-массива вывода скрипта")
    fun parses_roles_from_the_script_output() {
        val output = "started logging into a pipe\n[\"roles.httpd\",\"bootstrap.app\"]\n"
        assertEquals(listOf("roles.httpd", "bootstrap.app"), DebugLaunch.parseRoles(output))
    }

    @Test
    @DisplayName("пустой массив ролей — инстанс без ролей, а не отказ")
    fun parses_an_empty_role_list() {
        assertEquals(emptyList<String>(), DebugLaunch.parseRoles("[]\n"))
    }

    @Test
    @DisplayName("вывод без массива означает, что роли неизвестны")
    fun reports_unknown_roles_when_there_is_no_array() {
        assertNull(DebugLaunch.parseRoles("конфигурация инстанса не собрана\n"))
    }
}
