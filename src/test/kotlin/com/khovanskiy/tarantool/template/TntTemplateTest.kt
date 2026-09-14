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
