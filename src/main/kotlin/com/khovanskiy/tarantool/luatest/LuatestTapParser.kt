package com.khovanskiy.tarantool.luatest

/**
 * Разбор TAP-вывода luatest в события тестов.
 *
 * Реальный формат (luatest scm-1, Tarantool 3.8):
 * ```
 * TAP version 13
 * 1..4
 * # Started on Thu Aug 13 ...
 * # Starting group: demo
 * ok     1	demo.test_ok
 * not ok 2	demo.test_fail
 * #   .../demo_test.lua:9: сообщение об ошибке
 * #   expected: 3, actual: 2
 * # Ran 4 tests in 0.001 seconds ...
 * ```
 *
 * Диагностика падения идёт ПОСЛЕ строки `not ok`, поэтому событие провала
 * откладывается до следующего маркера. Класс не зависит от платформы
 * и покрыт юнит-тестами.
 */
class LuatestTapParser(private val listener: Listener) {

    interface Listener {
        fun onTestCount(count: Int)
        fun onSuiteStarted(name: String)
        fun onSuiteFinished(name: String)
        fun onTestPassed(suite: String, test: String)
        fun onTestFailed(suite: String, test: String, message: String)
        fun onOutput(line: String)
    }

    private var currentSuite: String? = null
    private var pendingSuite: String? = null
    private var pendingTest: String? = null
    private val pendingMessage = StringBuilder()

    fun line(raw: String) {
        val line = raw.trimEnd('\r')

        PLAN.find(line)?.let { plan ->
            flushPending()
            listener.onTestCount(plan.groupValues[1].toInt())
            listener.onOutput(line)
            return
        }

        OK.find(line)?.let { ok ->
            flushPending()
            val (suite, test) = splitName(ok.groupValues[1])
            ensureSuite(suite)
            listener.onTestPassed(suite, test)
            listener.onOutput(line)
            return
        }

        NOT_OK.find(line)?.let { notOk ->
            flushPending()
            val (suite, test) = splitName(notOk.groupValues[1])
            ensureSuite(suite)
            pendingSuite = suite
            pendingTest = test
            listener.onOutput(line)
            return
        }

        if (line.startsWith("#")) {
            if (pendingTest != null) {
                if (pendingMessage.isNotEmpty()) {
                    pendingMessage.append('\n')
                }
                pendingMessage.append(line.removePrefix("#").trim())
            }
            listener.onOutput(line)
            return
        }

        listener.onOutput(line)
    }

    /** Вызывается по завершении процесса: доигрывает отложенные события. */
    fun finish() {
        flushPending()
        currentSuite?.let { listener.onSuiteFinished(it) }
        currentSuite = null
    }

    private fun flushPending() {
        val suite = pendingSuite ?: return
        val test = pendingTest ?: return
        listener.onTestFailed(suite, test, pendingMessage.toString().ifBlank { "тест провален" })
        pendingSuite = null
        pendingTest = null
        pendingMessage.setLength(0)
    }

    private fun ensureSuite(suite: String) {
        if (suite == currentSuite) {
            return
        }
        currentSuite?.let { listener.onSuiteFinished(it) }
        listener.onSuiteStarted(suite)
        currentSuite = suite
    }

    /** `demo.test_ok` -> suite `demo`, тест `test_ok`; без точки — suite по умолчанию. */
    private fun splitName(full: String): Pair<String, String> {
        val name = full.trim()
        val dot = name.indexOf('.')
        return if (dot > 0) {
            name.substring(0, dot) to name.substring(dot + 1)
        } else {
            DEFAULT_SUITE to name
        }
    }

    private companion object {
        val PLAN = Regex("""^1\.\.(\d+)$""")
        val OK = Regex("""^ok\s+\d+\s+(.+)$""")
        val NOT_OK = Regex("""^not ok\s+\d+\s+(.+)$""")
        const val DEFAULT_SUITE = "tests"
    }
}
