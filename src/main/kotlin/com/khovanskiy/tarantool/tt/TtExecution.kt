package com.khovanskiy.tarantool.tt

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.khovanskiy.tarantool.settings.TarantoolProjectSettings
import com.khovanskiy.tarantool.settings.TarantoolRunMode
import java.nio.charset.StandardCharsets

/**
 * Единая точка построения команд tt и kubectl с учётом режима проекта.
 *
 * LOCAL: `tt <args>`; DOCKER: `<префикс> tt <args>` — префикс исполняет
 * команду внутри контейнера. Kubernetes-команды строятся отдельно:
 * там «инстанс» — это под, и tt в управлении не участвует.
 */
object TtExecution {

    fun mode(project: Project): TarantoolRunMode = TarantoolProjectSettings.getInstance(project).mode

    /**
     * Префикс исполнения tt внутри контейнера — распарсенный на аргументы.
     * Пустой список, когда режим не Docker или префикс не задан
     * (тогда исполнение откатывается на локальный tt).
     */
    fun dockerPrefixOrEmpty(project: Project): List<String> {
        val settings = TarantoolProjectSettings.getInstance(project)
        if (settings.mode != TarantoolRunMode.DOCKER) {
            return emptyList()
        }
        return ParametersListUtil.parse(settings.dockerExecPrefix)
    }

    /** Команда tt для текущего режима (LOCAL/DOCKER). */
    fun ttCommand(project: Project, vararg args: String): GeneralCommandLine {
        val prefix = dockerPrefixOrEmpty(project)
        val commandLine = if (prefix.isNotEmpty()) {
            GeneralCommandLine(prefix.first())
                .withParameters(prefix.drop(1))
                .withParameters("tt")
                .withParameters(*args)
        } else {
            GeneralCommandLine(TtCli.resolve(null)).withParameters(*args)
        }
        return commandLine
            .withWorkDirectory(project.basePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    /** Строка tt-команды для выполнения в Терминале (интерактив). */
    fun ttShellCommand(project: Project, args: String): String {
        val settings = TarantoolProjectSettings.getInstance(project)
        val prefix = dockerPrefixOrEmpty(project)
        return if (prefix.isNotEmpty()) {
            settings.dockerExecPrefix.trim() + " tt " + args
        } else {
            "'" + TtCli.resolve(null) + "' " + args
        }
    }

    /** Команда kubectl с namespace из настроек. */
    fun kubectlCommand(project: Project, vararg args: String): GeneralCommandLine {
        val settings = TarantoolProjectSettings.getInstance(project)
        val commandLine = GeneralCommandLine("kubectl")
        if (settings.kubernetesNamespace.isNotBlank()) {
            commandLine.addParameters("-n", settings.kubernetesNamespace)
        }
        commandLine.addParameters(*args)
        return commandLine
            .withWorkDirectory(project.basePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    /** Строка kubectl-команды для Терминала. */
    fun kubectlShellCommand(project: Project, args: String): String {
        val settings = TarantoolProjectSettings.getInstance(project)
        val namespace = settings.kubernetesNamespace.takeIf { it.isNotBlank() }?.let { "-n $it " } ?: ""
        return "kubectl $namespace$args"
    }
}
