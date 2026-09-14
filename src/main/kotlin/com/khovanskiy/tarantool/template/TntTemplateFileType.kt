package com.khovanskiy.tarantool.template

import com.intellij.openapi.fileTypes.LanguageFileType
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons
import javax.swing.Icon

/** Тип файла шаблона: `<имя>.thtml.lua`, как `.blade.php` у Laravel. */
class TntTemplateFileType private constructor() : LanguageFileType(TntTemplateLanguage) {

    override fun getName(): String = "Tarantool template"

    override fun getDescription(): String = TarantoolBundle.message("filetype.template.description")

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = TarantoolIcons.Tarantool

    companion object {
        /** Полное расширение файла шаблона. */
        const val EXTENSION = "thtml.lua"

        /** Чем кончается имя файла шаблона. */
        const val SUFFIX = ".$EXTENSION"

        @JvmField
        val INSTANCE = TntTemplateFileType()
    }
}
