package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.health.VersionNumbers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VersionNumbersTest {

    @Test
    @DisplayName("Сравнение по первым двум компонентам")
    fun compares_major_minor() {
        assertTrue(VersionNumbers.isAtLeast("0.24.0", 0, 24))
        assertTrue(VersionNumbers.isAtLeast("0.25", 0, 24))
        assertTrue(VersionNumbers.isAtLeast("1.0", 0, 24))
        assertFalse(VersionNumbers.isAtLeast("0.23.9", 0, 24))
        assertFalse(VersionNumbers.isAtLeast("0.9.19", 0, 24))
    }

    @Test
    @DisplayName("Нечисловые хвосты и мусор не ломают сравнение")
    fun tolerates_garbage() {
        assertTrue(VersionNumbers.isAtLeast("0.24-beta.1", 0, 24))
        assertTrue(VersionNumbers.isAtLeast("0.24rc2.5", 0, 24))
        assertFalse(VersionNumbers.isAtLeast("", 0, 24))
        assertFalse(VersionNumbers.isAtLeast("garbage", 0, 24))
    }
}
