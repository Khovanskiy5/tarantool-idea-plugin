package com.khovanskiy.tarantool.luatest

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.util.execution.ParametersListUtil
import java.io.File
import java.nio.charset.StandardCharsets

/** Запуск luatest с деревом результатов. */
class LuatestCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: LuatestRunConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val workDir = configuration.resolveWorkingDirectory()
        val luatest = File(workDir, configuration.expandMacros(configuration.luatestPath))

        val commandLine = GeneralCommandLine()
            .withExePath(luatest.path)
            .withWorkDirectory(workDir)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        // TAP — машиночитаемый формат, из него строится дерево тестов.
        commandLine.addParameters("-o", "tap")
        ParametersListUtil.parse(configuration.expandMacros(configuration.programArguments))
            .forEach(commandLine::addParameter)
        configuration.expandMacros(configuration.testPath)
            .takeIf { it.isNotBlank() }
            ?.let(commandLine::addParameter)

        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val handler = startProcess()
        val properties = LuatestConsoleProperties(configuration, executor)
        val console = SMTestRunnerConnectionUtil.createAndAttachConsole(FRAMEWORK_NAME, handler, properties)
        return DefaultExecutionResult(console, handler)
    }

    companion object {
        const val FRAMEWORK_NAME = "Luatest"
    }
}

/** Свойства консоли: подключают собственный разбор TAP-вывода. */
class LuatestConsoleProperties(
    configuration: LuatestRunConfiguration,
    executor: Executor,
) : SMTRunnerConsoleProperties(configuration, LuatestCommandLineState.FRAMEWORK_NAME, executor),
    SMCustomMessagesParsing {

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties,
    ): OutputToGeneralTestEventsConverter = LuatestOutputConverter(testFrameworkName, consoleProperties)
}
