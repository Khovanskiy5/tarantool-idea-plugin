package com.khovanskiy.tarantool.tt

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.util.execution.ParametersListUtil
import com.khovanskiy.tarantool.run.TarantoolTracebackFilter
import java.nio.charset.StandardCharsets

/**
 * Запуск команды tt в псевдотерминале: цветной вывод работает в окне Run
 * без внешнего терминала (интерактивный connect — не сюда, а в Терминал).
 *
 * В Docker-режиме проекта команда исполняется внутри контейнера через
 * настроенный префикс: <префикс> tt <команда>. Поле «Путь к tt»
 * конфигурации в этом режиме не участвует.
 */
class TtCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: TtRunConfiguration,
) : CommandLineState(environment) {

    init {
        // Пути file.lua:line в журналах и трейсбеках — кликабельные ссылки.
        consoleBuilder.addFilter(
            TarantoolTracebackFilter(configuration.project, configuration.resolveWorkingDirectory()),
        )
    }

    override fun startProcess(): ProcessHandler {
        val prefix = TtExecution.dockerPrefixOrEmpty(configuration.project)
        val commandLine: GeneralCommandLine = PtyCommandLine()
            .apply {
                if (prefix.isNotEmpty()) {
                    withExePath(prefix.first())
                    addParameters(prefix.drop(1))
                    addParameter("tt")
                } else {
                    withExePath(TtCli.resolve(configuration.expandMacros(configuration.ttPath)))
                }
            }
            .withWorkDirectory(configuration.resolveWorkingDirectory())
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(
                if (configuration.passParentEnvs) {
                    GeneralCommandLine.ParentEnvironmentType.CONSOLE
                } else {
                    GeneralCommandLine.ParentEnvironmentType.NONE
                }
            )

        ParametersListUtil.parse(configuration.expandMacros(configuration.command))
            .forEach(commandLine::addParameter)

        // tt передаёт окружение запускаемым инстансам — так конфигурация
        // «tt start» умеет поднимать кластер с параметрами отладчика.
        commandLine.environment.putAll(configuration.envs)

        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
