package com.khovanskiy.tarantool.tt

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.khovanskiy.tarantool.TarantoolBundle

/** Сохраняемые параметры конфигурации tt. */
class TtRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var command by string("start")
    var ttPath by string(TtCli.DEFAULT_NAME)
    var workingDirectory by string("")
}

/**
 * Конфигурация запуска команды tt: start, stop, status, connect, check и
 * любые другие. Команда выполняется в PTY, поэтому интерактивные сценарии
 * вроде `tt connect` работают прямо в окне Run.
 */
class TtRunConfiguration(project: Project, factory: ConfigurationFactory) :
    LocatableConfigurationBase<TtRunConfigurationOptions>(project, factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    public override fun getOptions(): TtRunConfigurationOptions =
        super.getOptions() as TtRunConfigurationOptions

    var command: String
        get() = options.command.orEmpty()
        set(value) {
            options.command = value
        }

    var ttPath: String
        get() = options.ttPath.orEmpty()
        set(value) {
            options.ttPath = value
        }

    var workingDirectory: String
        get() = options.workingDirectory.orEmpty()
        set(value) {
            options.workingDirectory = value
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        TtSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        TtCommandLineState(environment, this)

    override fun suggestedName(): String? =
        command.takeIf { it.isNotBlank() }?.let { "tt $it" }

    override fun checkConfiguration() {
        if (command.isBlank()) {
            throw RuntimeConfigurationError(TarantoolBundle.message("tt.error.no.command"))
        }
    }

    fun expandMacros(value: String): String =
        ProgramParametersUtil.expandPathAndMacros(value, null, project).orEmpty()

    fun resolveWorkingDirectory(): String {
        val configured = expandMacros(workingDirectory)
        return configured.ifBlank { project.basePath.orEmpty() }
    }
}
