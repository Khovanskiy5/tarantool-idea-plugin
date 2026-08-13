package com.khovanskiy.tarantool.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.khovanskiy.tarantool.TarantoolBundle

/**
 * Контекст live-шаблонов: любой Lua-файл.
 *
 * Проверка идёт по расширению файла, а не по языку: язык Lua регистрирует
 * плагин EmmyLua2, зависеть от которого жёстко не хочется.
 */
class TarantoolLuaTemplateContext : TemplateContextType(TarantoolBundle.message("live.template.context.lua")) {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
        templateActionContext.file.name.endsWith(".lua", ignoreCase = true)
}
