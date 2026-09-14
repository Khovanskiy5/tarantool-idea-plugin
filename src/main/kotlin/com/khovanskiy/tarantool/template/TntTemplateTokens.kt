package com.khovanskiy.tarantool.template

import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** Токен языка шаблонов. */
class TntTemplateTokenType(debugName: String) : IElementType(debugName, TntTemplateLanguage) {
    override fun toString(): String = "TntTemplate:" + super.toString()
}

/** Токены и типы узлов языка шаблонов. */
object TntTemplateTokens {
    /** Кусок HTML между вкраплениями: его разбирает HTML-плагин. */
    @JvmField val HTML = TntTemplateTokenType("HTML")

    /** Вкрапление внутри HTML-дерева: то, что видит HTML-парсер на месте шаблона. */
    @JvmField val OUTER = OuterLanguageElementType("TNT_OUTER", TntTemplateLanguage)

    /** Заметка `{{-- … --}}` целиком. */
    @JvmField val COMMENT = TntTemplateTokenType("COMMENT")

    /** Скобки вывода `{{` и `}}`. */
    @JvmField val OUTPUT_START = TntTemplateTokenType("OUTPUT_START")
    @JvmField val OUTPUT_END = TntTemplateTokenType("OUTPUT_END")

    /** Скобки сырого вывода `{!!` и `!!}`. */
    @JvmField val RAW_START = TntTemplateTokenType("RAW_START")
    @JvmField val RAW_END = TntTemplateTokenType("RAW_END")

    /** Директива с именем: `@if`, `@endsection`, `@money`. */
    @JvmField val DIRECTIVE = TntTemplateTokenType("DIRECTIVE")

    /** Выражение Lua: внутри скобок вывода либо доводы директивы в скобках. */
    @JvmField val LUA = TntTemplateTokenType("LUA")

    /** Скобки доводов директивы. */
    @JvmField val LPAREN = TntTemplateTokenType("LPAREN")
    @JvmField val RPAREN = TntTemplateTokenType("RPAREN")

    /** Экранированный знак: `@@` и `@{{` — в вывод идёт без первого `@`. */
    @JvmField val ESCAPE = TntTemplateTokenType("ESCAPE")

    @JvmField val COMMENTS: TokenSet = TokenSet.create(COMMENT)

    @JvmField val FILE = IFileElementType("TNT_TEMPLATE_FILE", TntTemplateLanguage)

    /** Данные HTML между вкраплениями — для отдельного HTML-дерева файла. */
    @JvmField val TEMPLATE_DATA = TemplateDataElementType("TNT_TEMPLATE_DATA", TntTemplateLanguage, HTML, OUTER)
}
