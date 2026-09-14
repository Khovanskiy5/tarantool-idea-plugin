package com.khovanskiy.tarantool.template

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext

/**
 * Дополнение директив после `@`: встроенные с их скобками и закрывающими.
 *
 * Свои директивы приложения плагину неизвестны — их дополняет
 * история ввода платформы.
 */
class TntTemplateCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(TntTemplateLanguage),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val prefix = directivePrefix(parameters) ?: return
                    val matched = result.withPrefixMatcher(prefix)

                    for (directive in TntTemplateDirectives.BUILT_IN) {
                        var element = LookupElementBuilder.create(directive.name)
                            .withPresentableText("@" + directive.name)
                            .withTypeText(if (directive.closer != null) "… @" + directive.closer else "", true)
                        if (directive.argument) {
                            element = element.withTailText("(…)", true).withInsertHandler { insertion, _ ->
                                EditorModificationUtil.insertStringAtCaret(insertion.editor, "()", false, 1)
                            }
                        }
                        matched.addElement(element)
                    }
                }
            },
        )
    }

    companion object {
        /**
         * Что набрано после `@` перед курсором; пусто — курсор не в директиве.
         *
         * Считается по тексту, а не по дереву: дополнение зовётся с копией
         * файла, где на месте курсора стоит служебное слово платформы.
         */
        fun directivePrefix(parameters: CompletionParameters): String? {
            val text = parameters.originalFile.text
            val offset = parameters.offset.coerceAtMost(text.length)
            var start = offset
            while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
            if (start == 0 || text[start - 1] != '@') return null
            if (start >= 2 && text[start - 2] == '@') return null
            return text.substring(start, offset)
        }
    }
}

/** Всплывающее дополнение сразу после набранного `@`. */
class TntTemplateTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped == '@' && file.language === TntTemplateLanguage) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            return Result.STOP
        }
        return Result.CONTINUE
    }
}
