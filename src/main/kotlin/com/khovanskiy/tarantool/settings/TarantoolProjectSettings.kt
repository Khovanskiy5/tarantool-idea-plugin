package com.khovanskiy.tarantool.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Режим исполнения команд Tarantool для проекта.
 *
 * LOCAL — инстансами управляет локальный tt (как раньше).
 * DOCKER — tt-команды исполняются внутри контейнера через настраиваемый
 * префикс (например, `docker compose exec tarantool`).
 * KUBERNETES — инстансы это поды: панель работает через kubectl
 * (get pods, logs, delete), консоль — kubectl exec.
 */
enum class TarantoolRunMode { LOCAL, DOCKER, KUBERNETES }

/** Проектные настройки плагина: .idea/tarantool.xml. */
@Service(Service.Level.PROJECT)
@State(name = "TarantoolProjectSettings", storages = [Storage("tarantool.xml")])
class TarantoolProjectSettings : PersistentStateComponent<TarantoolProjectSettings.State> {

    class State {
        var mode: TarantoolRunMode = TarantoolRunMode.LOCAL
        var dockerExecPrefix: String = "docker compose exec tarantool"
        var kubernetesNamespace: String = ""
        var kubernetesPodSelector: String = "app=tarantool"
        var kubernetesConsoleCommand: String = "console"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var mode: TarantoolRunMode
        get() = state.mode
        set(value) {
            state.mode = value
        }

    var dockerExecPrefix: String
        get() = state.dockerExecPrefix
        set(value) {
            state.dockerExecPrefix = value
        }

    var kubernetesNamespace: String
        get() = state.kubernetesNamespace
        set(value) {
            state.kubernetesNamespace = value
        }

    var kubernetesPodSelector: String
        get() = state.kubernetesPodSelector
        set(value) {
            state.kubernetesPodSelector = value
        }

    var kubernetesConsoleCommand: String
        get() = state.kubernetesConsoleCommand
        set(value) {
            state.kubernetesConsoleCommand = value
        }

    companion object {
        fun getInstance(project: Project): TarantoolProjectSettings =
            project.getService(TarantoolProjectSettings::class.java)
    }
}
