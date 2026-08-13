package com.khovanskiy.tarantool.toolwindow

import com.intellij.openapi.actionSystem.DataKey

/** Ключи данных панели «Tarantool» для действий из других модулей. */
object TarantoolDataKeys {

    /** Имя инстанса, выбранного в таблице панели (`app:instance`). */
    @JvmField
    val SELECTED_INSTANCE: DataKey<String> = DataKey.create("tarantool.selected.instance")
}
