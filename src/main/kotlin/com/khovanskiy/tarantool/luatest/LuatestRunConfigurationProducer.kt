package com.khovanskiy.tarantool.luatest

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/**
 * Создаёт конфигурацию luatest из контекста тестового файла.
 * Тестовыми считаются файлы с именами по конвенции luatest:
 * `*_test.lua` и `test_*.lua`.
 */
class LuatestRunConfigurationProducer : LazyRunConfigurationProducer<LuatestRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory = LuatestConfigurationType.instance

    override fun setupConfigurationFromContext(
        configuration: LuatestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = testFile(context) ?: return false
        val basePath = context.project.basePath.orEmpty()
        configuration.testPath = FileUtil.getRelativePath(basePath, file.path, '/') ?: file.path
        configuration.workingDirectory = basePath
        configuration.setGeneratedName()
        return true
    }

    override fun isConfigurationFromContext(
        configuration: LuatestRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = testFile(context) ?: return false
        val basePath = context.project.basePath.orEmpty()
        val relative = FileUtil.getRelativePath(basePath, file.path, '/') ?: file.path
        return FileUtil.pathsEqual(configuration.testPath, relative)
    }

    private fun testFile(context: ConfigurationContext): VirtualFile? {
        val file = context.location?.virtualFile ?: return null
        if (file.isDirectory || !isTestFileName(file.name)) {
            return null
        }
        return file
    }

    private fun isTestFileName(name: String): Boolean =
        name.endsWith("_test.lua") || (name.startsWith("test_") && name.endsWith(".lua"))
}
