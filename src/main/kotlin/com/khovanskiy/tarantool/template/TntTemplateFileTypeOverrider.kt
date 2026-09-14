package com.khovanskiy.tarantool.template

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Отдаёт файлы `*.thtml.lua` типу шаблона раньше, чем их заберёт Lua.
 *
 * Тип файла по расширению определяет последний сегмент имени, и `lua`
 * там уже занят плагином Lua; образец `*.thtml.lua` в объявлении типа
 * стоит в очереди после точных имён, и полагаться на порядок опроса
 * не хочется. Переопределение идёт до всех таблиц соответствия.
 */
class TntTemplateFileTypeOverrider : FileTypeOverrider {

    override fun getOverriddenFileType(file: VirtualFile): FileType? =
        if (file.name.endsWith(TntTemplateFileType.SUFFIX)) TntTemplateFileType.INSTANCE else null
}
