package com.khovanskiy.tarantool.template

import com.intellij.lang.Commenter

/** Заметка шаблона: `{{-- … --}}`, строчной формы у неё нет. */
class TntTemplateCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null
    override fun getBlockCommentPrefix(): String = "{{--"
    override fun getBlockCommentSuffix(): String = "--}}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
