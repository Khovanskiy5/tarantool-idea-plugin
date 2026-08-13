package com.khovanskiy.tarantool.run

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons

/**
 * Тип конфигурации запуска «Tarantool».
 *
 * SimpleConfigurationType совмещает тип и единственную фабрику — для
 * конфигурации с одним видом запусков это рекомендуемый вариант.
 */
class TarantoolConfigurationType : SimpleConfigurationType(
    ID,
    TarantoolBundle.message("run.configuration.name"),
    TarantoolBundle.message("run.configuration.description"),
    NotNullLazyValue.createValue { TarantoolIcons.Tarantool },
), DumbAware {

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        TarantoolRunConfiguration(project, this)

    override fun getOptionsClass(): Class<out BaseState> = TarantoolRunConfigurationOptions::class.java

    companion object {
        const val ID = "TarantoolRunConfiguration"

        val instance: TarantoolConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(TarantoolConfigurationType::class.java)
    }
}
