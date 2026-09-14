package com.khovanskiy.tarantool.template

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** Файл шаблона в дереве PSI. */
class TntTemplateFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TntTemplateLanguage) {

    override fun getFileType(): FileType = TntTemplateFileType.INSTANCE

    override fun toString(): String = "TntTemplateFile:$name"
}

/**
 * Разбор шаблона: плоское дерево из токенов лексера.
 *
 * Структуры — блоков `@if … @endif`, кусков `@section` — здесь нет:
 * подсветке и HTML-дереву она не нужна, а проверять парность блоков
 * умеет сам переводчик tnt-template и говорит об этом строкой.
 */
class TntTemplateParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = TntTemplateLexer()

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val file = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        file.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = TntTemplateTokens.FILE

    override fun getCommentTokens(): TokenSet = TntTemplateTokens.COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = LeafPsiElement(node.elementType, node.text)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = TntTemplateFile(viewProvider)
}
