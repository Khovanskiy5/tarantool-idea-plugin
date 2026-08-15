package com.khovanskiy.tarantool.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.util.execution.ParametersListUtil
import com.khovanskiy.tarantool.debugger.DebugLaunch
import java.nio.charset.StandardCharsets

/**
 * Сборка командной строки и запуск процесса tarantool.
 *
 * При графической отладке (`debugLaunch != null`) в командную строку
 * добавляется чанк `-e`: он выполняется до пользовательского скрипта,
 * поднимает агент отладчика и придерживает запуск, пока не подключится
 * IDE. Скрипт при этом не меняется — ни строки в исходниках проекта.
 */
class TarantoolCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: TarantoolRunConfiguration,
    private val debugLaunch: DebugLaunch? = null,
) : CommandLineState(environment) {

    init {
        // Ссылки file.lua:line в выводе процесса становятся кликабельными.
        consoleBuilder.addFilter(TarantoolTracebackFilter(configuration.project, configuration.resolveWorkingDirectory()))
    }

    override fun startProcess(): ProcessHandler {
        val workDir = configuration.resolveWorkingDirectory()

        // Встроенный отладчик tarantool -d — интерактивная консоль luadebug,
        // ей нужен псевдотерминал. С графической отладкой он не совмещается:
        // одну строку кода двумя отладчиками не пройти.
        val useLuaDebugger = configuration.useLuaDebugger && debugLaunch == null
        val base = if (useLuaDebugger) PtyCommandLine() else GeneralCommandLine()

        val commandLine = base
            .withExePath(TarantoolInterpreter.resolve(configuration.expandMacros(configuration.interpreterPath)))
            .withWorkDirectory(workDir)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(
                if (configuration.passParentEnvs) {
                    GeneralCommandLine.ParentEnvironmentType.CONSOLE
                } else {
                    GeneralCommandLine.ParentEnvironmentType.NONE
                }
            )

        if (useLuaDebugger) {
            commandLine.addParameter("-d")
        }
        debugLaunch?.let { commandLine.addParameters("-e", it.bootstrapChunk()) }
        commandLine.addParameter(configuration.expandMacros(configuration.scriptPath))
        ParametersListUtil.parse(configuration.expandMacros(configuration.programArguments))
            .forEach(commandLine::addParameter)

        commandLine.environment.putAll(configuration.envs)
        debugLaunch?.let { commandLine.environment.putAll(it.environment()) }

        if (configuration.augmentLuaPath) {
            // Пользовательское значение LUA_PATH сохраняется хвостом,
            // иначе дописывается ';;' — пути интерпретатора по умолчанию.
            val existing = configuration.envs["LUA_PATH"] ?: System.getenv("LUA_PATH")
            commandLine.environment["LUA_PATH"] = LuaPaths.build(workDir, existing)
        }

        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
