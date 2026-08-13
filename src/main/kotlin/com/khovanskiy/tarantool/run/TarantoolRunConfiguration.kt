package com.khovanskiy.tarantool.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.khovanskiy.tarantool.TarantoolBundle
import java.io.File

/** Сохраняемые параметры конфигурации. */
class TarantoolRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var scriptPath by string("")
    var interpreterPath by string(TarantoolInterpreter.DEFAULT_NAME)
    var programArguments by string("")
    var workingDirectory by string("")
    var augmentLuaPath by property(true)
    var useLuaDebugger by property(false)
    var passParentEnvs by property(true)
    var envs by map<String, String>()
}

/**
 * Конфигурация запуска Lua-скрипта интерпретатором Tarantool.
 *
 * Отладчик у конфигурации отсутствует (Emmy-отладку даёт плагин EmmyLua2
 * собственной конфигурацией), поэтому стандартная кнопка Debug подавлена.
 */
class TarantoolRunConfiguration(project: Project, factory: ConfigurationFactory) :
    LocatableConfigurationBase<TarantoolRunConfigurationOptions>(project, factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    public override fun getOptions(): TarantoolRunConfigurationOptions =
        super.getOptions() as TarantoolRunConfigurationOptions

    var scriptPath: String
        get() = options.scriptPath.orEmpty()
        set(value) {
            options.scriptPath = value
        }

    var interpreterPath: String
        get() = options.interpreterPath.orEmpty()
        set(value) {
            options.interpreterPath = value
        }

    var programArguments: String
        get() = options.programArguments.orEmpty()
        set(value) {
            options.programArguments = value
        }

    var workingDirectory: String
        get() = options.workingDirectory.orEmpty()
        set(value) {
            options.workingDirectory = value
        }

    var augmentLuaPath: Boolean
        get() = options.augmentLuaPath
        set(value) {
            options.augmentLuaPath = value
        }

    var useLuaDebugger: Boolean
        get() = options.useLuaDebugger
        set(value) {
            options.useLuaDebugger = value
        }

    var passParentEnvs: Boolean
        get() = options.passParentEnvs
        set(value) {
            options.passParentEnvs = value
        }

    var envs: Map<String, String>
        get() = options.envs
        set(value) {
            options.envs = value.toMutableMap()
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        TarantoolSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        TarantoolCommandLineState(environment, this)

    override fun suggestedName(): String? =
        scriptPath.takeIf { it.isNotBlank() }?.let { File(it).name }

    override fun checkConfiguration() {
        if (scriptPath.isBlank()) {
            throw RuntimeConfigurationError(TarantoolBundle.message("error.no.script"))
        }
        val script = expandMacros(scriptPath)
        if (!File(script).isFile) {
            throw RuntimeConfigurationWarning(TarantoolBundle.message("warning.script.not.found", script))
        }
        val workDir = expandMacros(workingDirectory)
        if (workDir.isNotBlank() && !File(workDir).isDirectory) {
            throw RuntimeConfigurationWarning(TarantoolBundle.message("warning.working.dir.not.found", workDir))
        }
    }

    /** Разворачивает макросы вида $PROJECT_DIR$ и $FilePath$. */
    fun expandMacros(value: String): String =
        ProgramParametersUtil.expandPathAndMacros(value, null, project).orEmpty()

    /** Рабочий каталог с учётом значения по умолчанию — корня проекта. */
    fun resolveWorkingDirectory(): String {
        val configured = expandMacros(workingDirectory)
        if (configured.isNotBlank()) {
            return FileUtil.toSystemDependentName(configured)
        }
        return project.basePath.orEmpty()
    }
}
