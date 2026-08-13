package com.khovanskiy.tarantool.tt

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons

/** Тип конфигурации запуска команд tt — CLI для управления приложениями Tarantool. */
class TtConfigurationType : SimpleConfigurationType(
    ID,
    TarantoolBundle.message("tt.run.configuration.name"),
    TarantoolBundle.message("tt.run.configuration.description"),
    NotNullLazyValue.createValue { TarantoolIcons.Tarantool },
), DumbAware {

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        TtRunConfiguration(project, this)

    override fun getOptionsClass(): Class<out BaseState> = TtRunConfigurationOptions::class.java

    companion object {
        const val ID = "TarantoolTtRunConfiguration"

        val instance: TtConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(TtConfigurationType::class.java)
    }
}
