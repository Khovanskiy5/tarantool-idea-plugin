package com.khovanskiy.tarantool.tt

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.khovanskiy.tarantool.settings.TarantoolSettings
import java.io.File

/** Поиск исполняемого файла tt. */
object TtCli {

    const val DEFAULT_NAME = "tt"

    private val WELL_KNOWN_LOCATIONS = listOf(
        "/opt/homebrew/bin/tt",
        "/usr/local/bin/tt",
        "/usr/bin/tt",
    )

    fun resolve(configured: String?): String {
        val value = configured?.trim().orEmpty()
        if (value.isNotEmpty() && value != DEFAULT_NAME) {
            return value
        }
        TarantoolSettings.getInstance().ttPath.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        PathEnvironmentVariableUtil.findInPath(DEFAULT_NAME)?.let { return it.absolutePath }
        WELL_KNOWN_LOCATIONS.firstOrNull { File(it).canExecute() }?.let { return it }
        return DEFAULT_NAME
    }
}
