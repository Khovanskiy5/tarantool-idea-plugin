package com.khovanskiy.tarantool.template

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

/**
 * Язык шаблонов страниц tnt-template (`*.thtml.lua`) — аналог Blade.
 *
 * Шаблон — HTML с вкраплениями: `{{ выражение }}`, `{!! сырое !!}`,
 * `{{-- заметка --}}` и `@директивы` с доводами в скобках. Это язык
 * шаблонов над HTML: разметку разбирает и подсвечивает HTML-плагин
 * платформы, а вкрапления — этот модуль. Без него файл разбирался бы
 * как Lua и был бы красным целиком.
 */
object TntTemplateLanguage : Language("TntTemplate"), TemplateLanguage {

    private fun readResolve(): Any = TntTemplateLanguage

    override fun getDisplayName(): String = "Tarantool template"
}
