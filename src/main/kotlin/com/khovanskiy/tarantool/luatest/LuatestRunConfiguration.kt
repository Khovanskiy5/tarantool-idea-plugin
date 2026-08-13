package com.khovanskiy.tarantool.luatest

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons
import java.io.File

/** Тип конфигурации запуска тестов luatest. */
class LuatestConfigurationType : SimpleConfigurationType(
    ID,
    TarantoolBundle.message("luatest.run.configuration.name"),
    TarantoolBundle.message("luatest.run.configuration.description"),
    NotNullLazyValue.createValue { TarantoolIcons.Tarantool },
), DumbAware {

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        LuatestRunConfiguration(project, this)

    override fun getOptionsClass(): Class<out BaseState> = LuatestRunConfigurationOptions::class.java

    companion object {
        const val ID = "TarantoolLuatestRunConfiguration"

        val instance: LuatestConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(LuatestConfigurationType::class.java)
    }
}

/** Сохраняемые параметры конфигурации luatest. */
class LuatestRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var testPath by string("")
    var luatestPath by string(DEFAULT_LUATEST_PATH)
    var programArguments by string("-v")
    var workingDirectory by string("")

    companion object {
        const val DEFAULT_LUATEST_PATH = ".rocks/bin/luatest"
    }
}

/**
 * Конфигурация запуска luatest — тестового фреймворка Tarantool.
 * Результаты показываются деревом тестов (SMTestRunner).
 */
class LuatestRunConfiguration(project: Project, factory: ConfigurationFactory) :
    LocatableConfigurationBase<LuatestRunConfigurationOptions>(project, factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    public override fun getOptions(): LuatestRunConfigurationOptions =
        super.getOptions() as LuatestRunConfigurationOptions

    var testPath: String
        get() = options.testPath.orEmpty()
        set(value) {
            options.testPath = value
        }

    var luatestPath: String
        get() = options.luatestPath.orEmpty()
        set(value) {
            options.luatestPath = value
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

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        LuatestSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        LuatestCommandLineState(environment, this)

    override fun suggestedName(): String? {
        val path = testPath.ifBlank { return "luatest" }
        return "luatest: ${File(path).name}"
    }

    override fun checkConfiguration() {
        val luatest = File(resolveWorkingDirectory(), expandMacros(luatestPath))
        if (!luatest.canExecute()) {
            throw RuntimeConfigurationWarning(
                TarantoolBundle.message("luatest.warning.not.installed", luatest.path),
            )
        }
    }

    fun expandMacros(value: String): String =
        ProgramParametersUtil.expandPathAndMacros(value, null, project).orEmpty()

    fun resolveWorkingDirectory(): String {
        val configured = expandMacros(workingDirectory)
        return configured.ifBlank { project.basePath.orEmpty() }
    }
}
