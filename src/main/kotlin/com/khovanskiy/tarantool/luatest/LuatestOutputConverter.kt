package com.khovanskiy.tarantool.luatest

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.TestStarted
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted

/**
 * Транслирует TAP-вывод luatest в события дерева тестов.
 * Разбор строк — в [LuatestTapParser]; здесь только связка с процессором.
 */
class LuatestOutputConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties,
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties), LuatestTapParser.Listener {

    private val parser = LuatestTapParser(this)
    private var lastOutputType: Key<*> = ProcessOutputTypes.STDOUT

    override fun processConsistentText(text: String, outputType: Key<*>) {
        lastOutputType = outputType
        // Текст приходит уже нарезанным по строкам (включая перевод строки).
        parser.line(text.trimEnd('\n'))
    }

    override fun flushBufferOnProcessTermination(exitCode: Int) {
        parser.finish()
        super.flushBufferOnProcessTermination(exitCode)
    }

    override fun onTestCount(count: Int) {
        processor.onTestsCountInSuite(count)
    }

    override fun onSuiteStarted(name: String) {
        processor.onSuiteStarted(TestSuiteStartedEvent(TestSuiteStarted(name), null))
    }

    override fun onSuiteFinished(name: String) {
        processor.onSuiteFinished(TestSuiteFinishedEvent(name))
    }

    override fun onTestPassed(suite: String, test: String) {
        processor.onTestStarted(TestStartedEvent(TestStarted(test, false, null), null))
        processor.onTestFinished(TestFinishedEvent(test, null, null))
    }

    override fun onTestFailed(suite: String, test: String, message: String) {
        processor.onTestStarted(TestStartedEvent(TestStarted(test, false, null), null))
        processor.onTestFailure(TestFailedEvent(test, message, null, false, null, null))
        processor.onTestFinished(TestFinishedEvent(test, null, null))
    }

    override fun onOutput(line: String) {
        fireOnUncapturedOutput(line + "\n", lastOutputType)
    }
}
