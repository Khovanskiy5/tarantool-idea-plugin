package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.luatest.LuatestTapParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LuatestTapParserTest {

    /** Слушатель, протоколирующий события строками — удобно сравнивать целиком. */
    private class RecordingListener : LuatestTapParser.Listener {
        val events = mutableListOf<String>()
        override fun onTestCount(count: Int) { events += "count:$count" }
        override fun onSuiteStarted(name: String) { events += "suite+:$name" }
        override fun onSuiteFinished(name: String) { events += "suite-:$name" }
        override fun onTestPassed(suite: String, test: String) { events += "pass:$suite.$test" }
        override fun onTestFailed(suite: String, test: String, message: String) {
            events += "fail:$suite.$test:${message.lineSequence().first()}"
        }
        override fun onOutput(line: String) { /* вывод в консоль здесь не важен */ }
    }

    private fun run(lines: List<String>): List<String> {
        val listener = RecordingListener()
        val parser = LuatestTapParser(listener)
        lines.forEach(parser::line)
        parser.finish()
        return listener.events
    }

    @Test
    @DisplayName("Реальный вывод luatest разбирается в дерево событий")
    fun parses_real_output() {
        // Дословный вывод luatest scm-1 на Tarantool 3.8 (см. docs/development.md).
        val events = run(
            listOf(
                "Tarantool version is 3.8.0-0-gdce7be8",
                "started logging into a pipe, SIGHUP log rotation disabled",
                "TAP version 13",
                "1..4",
                "# Started on Thu Aug 13 14:24:54 2026",
                "# Starting group: demo",
                "ok     1\tdemo.test_ok",
                "not ok 2\tdemo.test_fail",
                "#   .../demo_test.lua:9: намеренное падение для проверки раннера",
                "#   expected: 3, actual: 2",
                "not ok 3\tdemo.test_error",
                "#   .../demo_test.lua:13: намеренная ошибка",
                "# Starting group: second",
                "ok     4\tsecond.test_also_ok",
                "# Ran 4 tests in 0.001 seconds, 2 succeeded, 1 failed, 1 errored",
            ),
        )

        assertEquals(
            listOf(
                "count:4",
                "suite+:demo",
                "pass:demo.test_ok",
                "fail:demo.test_fail:.../demo_test.lua:9: намеренное падение для проверки раннера",
                "fail:demo.test_error:.../demo_test.lua:13: намеренная ошибка",
                "suite-:demo",
                "suite+:second",
                "pass:second.test_also_ok",
                "suite-:second",
            ),
            events,
        )
    }

    @Test
    @DisplayName("Провал в конце потока доигрывается на finish")
    fun flushes_pending_failure_on_finish() {
        val events = run(
            listOf(
                "1..1",
                "not ok 1\tdemo.test_last",
                "#   сообщение",
            ),
        )
        assertEquals(
            listOf("count:1", "suite+:demo", "fail:demo.test_last:сообщение", "suite-:demo"),
            events,
        )
    }

    @Test
    @DisplayName("Имя без группы попадает в suite по умолчанию")
    fun default_suite_for_plain_names() {
        val events = run(listOf("ok 1\tstandalone_test"))
        assertEquals(listOf("suite+:tests", "pass:tests.standalone_test", "suite-:tests"), events)
    }
}
