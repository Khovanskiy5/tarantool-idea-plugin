package com.khovanskiy.tarantool.template

import com.intellij.ide.highlighter.HtmlFileHighlighter
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter

/** Цвета вкраплений шаблона: скобки, директивы, выражения, заметки. */
object TntTemplateColors {
    @JvmField val MARKER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("TNT_TEMPLATE_MARKER", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR)
    @JvmField val DIRECTIVE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("TNT_TEMPLATE_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD)
    @JvmField val EXPRESSION: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("TNT_TEMPLATE_EXPRESSION", DefaultLanguageHighlighterColors.IDENTIFIER)
    @JvmField val COMMENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("TNT_TEMPLATE_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    @JvmField val ESCAPE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("TNT_TEMPLATE_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
}

/** Подсветка вкраплений; HTML между ними красит слой HTML-подсветки. */
class TntTemplateSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = TntTemplateLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
        TntTemplateTokens.OUTPUT_START, TntTemplateTokens.OUTPUT_END,
        TntTemplateTokens.RAW_START, TntTemplateTokens.RAW_END,
        TntTemplateTokens.LPAREN, TntTemplateTokens.RPAREN,
        -> pack(TntTemplateColors.MARKER)
        TntTemplateTokens.DIRECTIVE -> pack(TntTemplateColors.DIRECTIVE)
        TntTemplateTokens.LUA -> pack(TntTemplateColors.EXPRESSION)
        TntTemplateTokens.COMMENT -> pack(TntTemplateColors.COMMENT)
        TntTemplateTokens.ESCAPE -> pack(TntTemplateColors.ESCAPE)
        else -> emptyArray()
    }
}

class TntTemplateSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        TntTemplateSyntaxHighlighter()
}

/**
 * Подсветка редактора: слой HTML поверх токенов [TntTemplateTokens.HTML].
 *
 * Так теги и атрибуты красит HTML-подсветка платформы, а вкрапления —
 * своя; без слоя HTML остался бы одноцветным.
 */
class TntTemplateEditorHighlighterProvider : EditorHighlighterProvider {

    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): EditorHighlighter {
        val highlighter = LayeredLexerEditorHighlighter(TntTemplateSyntaxHighlighter(), colors)
        highlighter.registerLayer(TntTemplateTokens.HTML, LayerDescriptor(HtmlFileHighlighter(), ""))
        return highlighter
    }
}
