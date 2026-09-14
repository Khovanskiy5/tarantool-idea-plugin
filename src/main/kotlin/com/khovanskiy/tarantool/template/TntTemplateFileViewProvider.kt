package com.khovanskiy.tarantool.template

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider

/**
 * Два дерева на один файл: шаблон и HTML.
 *
 * Как у Blade в PhpStorm: HTML-плагин видит разметку с «дырками»
 * на месте вкраплений и даёт своё — подсветку тегов, автозакрытие,
 * структуру, — а вкрапления живут в дереве шаблона.
 */
class TntTemplateFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled), TemplateLanguageFileViewProvider {

    override fun getBaseLanguage(): Language = TntTemplateLanguage

    override fun getTemplateDataLanguage(): Language = HTMLLanguage.INSTANCE

    override fun getLanguages(): Set<Language> = setOf(TntTemplateLanguage, HTMLLanguage.INSTANCE)

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        TntTemplateFileViewProvider(manager, fileCopy, false)

    override fun createFile(lang: Language): PsiFile? {
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null
        val file = definition.createFile(this)
        if (lang === HTMLLanguage.INSTANCE) {
            (file as PsiFileImpl).contentElementType = TntTemplateTokens.TEMPLATE_DATA
        }
        return file
    }
}

/** Фабрика поставщика деревьев для типа файла шаблона. */
class TntTemplateFileViewProviderFactory : FileViewProviderFactory {

    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean,
    ): FileViewProvider = TntTemplateFileViewProvider(manager, file, eventSystemEnabled)
}
