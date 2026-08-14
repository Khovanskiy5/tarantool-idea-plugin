package com.khovanskiy.tarantool.health

/** Числовое сравнение версий вида «0.24.1» по первым двум компонентам. */
object VersionNumbers {

    /**
     * true, когда версия не ниже major.minor. Нечисловые хвосты компонентов
     * отбрасываются («24-beta» → 24); пустая или нечисловая версия считается
     * нулевой.
     */
    fun isAtLeast(version: String, major: Int, minor: Int): Boolean {
        val parts = version.trim().split('.')
        val first = numericPrefix(parts.getOrElse(0) { "" })
        val second = numericPrefix(parts.getOrElse(1) { "" })
        return first > major || (first == major && second >= minor)
    }

    private fun numericPrefix(component: String): Int =
        component.takeWhile(Char::isDigit).toIntOrNull() ?: 0
}
