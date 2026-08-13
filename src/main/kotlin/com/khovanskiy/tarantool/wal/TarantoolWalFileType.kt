package com.khovanskiy.tarantool.wal

import com.intellij.openapi.fileTypes.FileType
import com.khovanskiy.tarantool.TarantoolBundle
import com.khovanskiy.tarantool.TarantoolIcons
import javax.swing.Icon

/**
 * Бинарные файлы данных Tarantool: снимки (.snap), журналы (.xlog)
 * и метажурналы vinyl (.vylog).
 *
 * Регистрация типа не даёт IDE открывать их как текст; содержимое
 * показывает действие «Показать содержимое (tt cat)».
 */
class TarantoolWalFileType private constructor() : FileType {

    override fun getName(): String = "Tarantool WAL"

    override fun getDescription(): String = TarantoolBundle.message("filetype.wal.description")

    override fun getDefaultExtension(): String = "xlog"

    override fun getIcon(): Icon = TarantoolIcons.Tarantool

    override fun isBinary(): Boolean = true

    companion object {
        @JvmField
        val INSTANCE = TarantoolWalFileType()

        val EXTENSIONS = setOf("snap", "xlog", "vylog")
    }
}
