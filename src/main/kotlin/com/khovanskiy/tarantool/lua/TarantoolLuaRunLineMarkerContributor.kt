package com.khovanskiy.tarantool.lua

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement

/**
 * Гуттер-иконка ▶ на первой строке Lua-файла: запуск через контекстные
 * конфигурации (Tarantool или luatest — их создают продюсеры плагина).
 *
 * Класс живёт в модуле, который грузится только вместе с плагином
 * EmmyLua2 — без него язык Lua в IDE не зарегистрирован.
 */
class TarantoolLuaRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // Иконка вешается ровно на первый лист-элемент файла.
        if (element.firstChild != null || element.textRange.startOffset != 0) {
            return null
        }
        return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
    }
}
