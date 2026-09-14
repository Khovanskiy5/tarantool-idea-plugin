package com.khovanskiy.tarantool.template

/**
 * Встроенные директивы tnt-template — для автодополнения и вставки скобок.
 *
 * Список повторяет `tnt/template/directives.lua` пакета: у директивы
 * с доводами после имени ставятся скобки. Свои директивы приложения
 * (`views:directive`) плагину неизвестны — их дополняет история ввода.
 */
object TntTemplateDirectives {

    /** Директива: имя, нужны ли скобки с доводами, чем закрывается блок. */
    data class Directive(val name: String, val argument: Boolean, val closer: String? = null)

    @JvmField
    val BUILT_IN: List<Directive> = listOf(
        Directive("if", argument = true, closer = "endif"),
        Directive("elseif", argument = true),
        Directive("else", argument = false),
        Directive("endif", argument = false),
        Directive("unless", argument = true, closer = "endunless"),
        Directive("endunless", argument = false),
        Directive("for", argument = true, closer = "endfor"),
        Directive("endfor", argument = false),
        Directive("extends", argument = true),
        Directive("section", argument = true, closer = "endsection"),
        Directive("endsection", argument = false),
        Directive("yield", argument = true),
        Directive("include", argument = true),
    )

    /** Директива по имени; пусто — своя или опечатка. */
    fun byName(name: String): Directive? = BUILT_IN.firstOrNull { it.name == name }

    /**
     * Как обернуть доводы директивы, чтобы вышел разбираемый кусок Lua:
     * условие — в `if … then end`, заголовок цикла — в `for … do end`,
     * остальное — доводами вызова.
     */
    fun luaWrapper(name: String): Pair<String, String> = when (name) {
        "if", "elseif", "unless" -> "if " to " then end"
        "for" -> "for " to " do end"
        else -> "local _ = _(" to ")"
    }
}
