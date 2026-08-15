package com.khovanskiy.tarantool.debugger

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import java.io.File

/**
 * Нативная библиотека emmy_core — отладочный агент, который загружается
 * в LuaJIT процесса Tarantool и принимает подключение IDE по TCP.
 *
 * Библиотека поставляется вместе с плагином EmmyLua2, поэтому её путь
 * известен точно: каталог плагина плюс подкаталог платформы. Раньше её
 * искал сам Lua-хелпер перебором каталогов JetBrains — это работало
 * только на машине разработчика с единственной установкой IDE.
 */
object EmmyCore {

    const val PLUGIN_ID = "com.cppcxy.Intellij-EmmyLua"

    /** Установлен ли (и включён) плагин EmmyLua2. */
    fun pluginEnabled(): Boolean = descriptor() != null

    /**
     * Каталог с emmy_core.dylib/so/dll для текущей платформы.
     * null — плагина нет либо в нём нет сборки под эту платформу.
     */
    fun nativeDirectory(): File? {
        val root = descriptor()?.pluginPath?.toFile()?.resolve(NATIVE_ROOT) ?: return null
        if (!root.isDirectory) {
            return null
        }
        return platformDirectories().map { File(root, it) }.firstOrNull { it.isDirectory }
    }

    private fun descriptor() =
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
            ?.takeIf { !PluginManagerCore.isDisabled(it.pluginId) }

    /** Кандидаты от точного совпадения по архитектуре к общему каталогу. */
    private fun platformDirectories(): List<String> = when {
        SystemInfo.isMac -> listOf(if (CpuArch.isArm64()) "mac/arm64" else "mac/x64", "mac")
        SystemInfo.isWindows -> listOf(if (CpuArch.is32Bit()) "windows/x86" else "windows/x64", "windows")
        else -> listOf("linux")
    }

    private const val NATIVE_ROOT = "debugger/emmy"
}
