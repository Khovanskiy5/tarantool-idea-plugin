package com.khovanskiy.tarantool.template

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Файл `*.thtml.lua` — шаблон, а не Lua: свой тип, два дерева (шаблон
 * и HTML) и лексер, который делит текст так же, как переводчик
 * tnt-template.
 */
class TntTemplateTest : BasePlatformTestCase() {

    fun `test file with thtml lua suffix is a template and not lua`() {
        val file = myFixture.configureByText("about.thtml.lua", "<p>{{ name }}</p>")

        assertEquals(TntTemplateFileType.INSTANCE, file.fileType)
        assertEquals(TntTemplateLanguage, file.language)
    }

    fun `test template file carries an html tree with holes for insertions`() {
        val file = myFixture.configureByText("about.thtml.lua", "<p>{{ name }}</p>@if (x)<b>да</b>@endif")
        val provider = file.viewProvider as TemplateLanguageFileViewProvider

        assertEquals(HTMLLanguage.INSTANCE, provider.templateDataLanguage)

        val html = provider.getPsi(HTMLLanguage.INSTANCE)

        assertNotNull(html)
        assertEquals(file.text, html!!.text)
    }

    fun `test lexer splits html from insertions`() {
        assertEquals(
            listOf(
                "HTML:<p>",
                "OUTPUT_START:{{",
                "LUA: name ",
                "OUTPUT_END:}}",
                "HTML:</p>\n",
                "DIRECTIVE:@if",
                "WHITE_SPACE: ",
                "LPAREN:(",
                "LUA:x == \")\" and f(1)",
                "RPAREN:)",
                "HTML:да",
                "DIRECTIVE:@endif",
                "HTML: ",
                "RAW_START:{!!",
                "LUA: raw ",
                "RAW_END:!!}",
                "COMMENT:{{-- заметка --}}",
                "ESCAPE:@@",
                "HTML:if ",
                "ESCAPE:@{{",
                "HTML: x }} duty",
                "DIRECTIVE:@example",
                "HTML:.org",
            ),
            tokens("<p>{{ name }}</p>\n@if (x == \")\" and f(1))да@endif {!! raw !!}{{-- заметка --}}@@if @{{ x }} duty@example.org"),
        )
    }

    fun `test unclosed insertions reach the end of the file without an error`() {
        assertEquals(listOf("OUTPUT_START:{{", "LUA: x"), tokens("{{ x"))
        assertEquals(listOf("COMMENT:{{-- x"), tokens("{{-- x"))
        assertEquals(listOf("DIRECTIVE:@if", "LPAREN:(", "LUA:x"), tokens("@if(x"))
        assertEquals(listOf("HTML:a", "HTML:@", "HTML: b"), tokens("a@ b"))
        assertEquals(listOf("DIRECTIVE:@endif", "LPAREN:(", "RPAREN:)"), tokens("@endif()"))
    }

    fun `test directives are completed after the at sign with their parentheses`() {
        myFixture.configureByText("about.thtml.lua", "<p>@i<caret></p>")

        val offered = myFixture.completeBasic().map { it.lookupString }

        assertTrue(offered.toString(), offered.containsAll(listOf("if", "include")))
        assertFalse(offered.toString(), offered.contains("for"))

        myFixture.configureByText("about.thtml.lua", "<p>@inc<caret></p>")
        myFixture.completeBasic()

        myFixture.checkResult("<p>@include(<caret>)</p>")

        // Без `@` и после `@@` директив не предлагается.
        myFixture.configureByText("about.thtml.lua", "<p>i<caret></p>")
        assertTrue(myFixture.completeBasic().orEmpty().none { it.lookupString == "if" })
        myFixture.configureByText("about.thtml.lua", "<p>@@i<caret></p>")
        assertTrue(myFixture.completeBasic().orEmpty().none { it.lookupString == "if" })
    }

    fun `test lua expressions are injection hosts wrapped into parsable lua`() {
        val file = myFixture.configureByText(
            "about.thtml.lua",
            "{{ name }}@if (x)@endif@for (_, v in ipairs(xs))@endfor@include('a', { b = 1 })",
        )
        val hosts = com.intellij.psi.util.PsiTreeUtil.collectElementsOfType(file, TntTemplateLuaElement::class.java).toList()

        assertEquals(listOf(" name ", "x", "_, v in ipairs(xs)", "'a', { b = 1 }"), hosts.map { it.text })
        assertEquals(
            listOf("return " to "", "if " to " then end", "for " to " do end", "local _ = _(" to ")"),
            hosts.map { TntTemplateLuaInjector.wrapperOf(it) },
        )
        assertTrue(hosts.all { it.isValidHost })
    }

    private fun tokens(text: String): List<String> {
        val lexer = TntTemplateLexer()
        val seen = mutableListOf<String>()
        lexer.start(text)
        while (lexer.tokenType != null) {
            seen.add(lexer.tokenType.toString().removePrefix("TntTemplate:") + ":" + lexer.tokenText)
            lexer.advance()
        }
        return seen
    }
}
