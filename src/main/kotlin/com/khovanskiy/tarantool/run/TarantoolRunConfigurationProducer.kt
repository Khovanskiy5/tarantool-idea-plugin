package com.khovanskiy.tarantool.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/**
 * Создаёт конфигурацию запуска из контекста .lua-файла: правый клик по файлу
 * или Run Context Configuration в редакторе.
 */
class TarantoolRunConfigurationProducer : LazyRunConfigurationProducer<TarantoolRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory = TarantoolConfigurationType.instance

    override fun setupConfigurationFromContext(
        configuration: TarantoolRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = luaFile(context) ?: return false
        configuration.scriptPath = file.path
        configuration.workingDirectory = context.project.basePath.orEmpty()
        configuration.setGeneratedName()
        return true
    }

    override fun isConfigurationFromContext(
        configuration: TarantoolRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = luaFile(context) ?: return false
        return FileUtil.pathsEqual(configuration.scriptPath, file.path)
    }

    private fun luaFile(context: ConfigurationContext): VirtualFile? {
        val file = context.location?.virtualFile ?: return null
        if (file.isDirectory || !file.name.endsWith(".lua")) {
            return null
        }
        return file
    }
}
