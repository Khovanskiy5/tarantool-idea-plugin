package com.khovanskiy.tarantool.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Настройки плагина уровня приложения: пути к исполняемым файлам
 * по умолчанию. Пустое значение — автопоиск в PATH и типовых каталогах.
 */
@Service
@State(name = "TarantoolSettings", storages = [Storage("tarantool.xml")])
class TarantoolSettings : PersistentStateComponent<TarantoolSettings.State> {

    class State {
        var tarantoolPath: String = ""
        var ttPath: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var tarantoolPath: String
        get() = state.tarantoolPath
        set(value) {
            state.tarantoolPath = value
        }

    var ttPath: String
        get() = state.ttPath
        set(value) {
            state.ttPath = value
        }

    companion object {
        fun getInstance(): TarantoolSettings =
            ApplicationManager.getApplication().getService(TarantoolSettings::class.java)
    }
}
