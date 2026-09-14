package com.khovanskiy.tarantool.template

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Лист-выражение шаблона: место для инъекции языка Lua.
 *
 * Выражение внутри `{{ }}` и доводы директивы — Lua, и с плагином Lua
 * они получают его подсветку. Сам лист остаётся листом: дерево шаблона
 * плоское.
 */
class TntTemplateLuaElement(type: IElementType, text: CharSequence) : LeafPsiElement(type, text), PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost {
        replaceWithText(text)
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}

/**
 * Инъекция Lua в выражения шаблона.
 *
 * Язык берётся по имени `Lua` — его даёт плагин EmmyLua2; без него
 * инъекции нет, и выражение подсвечивается одним цветом. Кусок
 * дополняется до разбираемого: вывод — `return …`, условие — `if … then
 * end`, цикл — `for … do end`, доводы — вызовом.
 */
class TntTemplateLuaInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(TntTemplateLuaElement::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val lua = Language.findLanguageByID(LUA_LANGUAGE_ID) ?: return
        val host = context as? TntTemplateLuaElement ?: return
        val (prefix, suffix) = wrapperOf(host)

        registrar.startInjecting(lua)
            .addPlace(prefix, suffix, host, TextRange(0, host.textLength))
            .doneInjecting()
    }

    companion object {
        /** Идентификатор языка Lua у плагина EmmyLua2. */
        const val LUA_LANGUAGE_ID = "Lua"

        /** Чем дополнить выражение по тому, что стоит перед ним. */
        fun wrapperOf(host: PsiElement): Pair<String, String> {
            val before = host.prevSibling
            if (before?.node?.elementType === TntTemplateTokens.LPAREN) {
                var directive = before.prevSibling
                while (directive != null && directive.node.elementType !== TntTemplateTokens.DIRECTIVE) {
                    directive = directive.prevSibling
                }
                return TntTemplateDirectives.luaWrapper(directive?.text?.removePrefix("@") ?: "")
            }
            return "return " to ""
        }
    }
}
