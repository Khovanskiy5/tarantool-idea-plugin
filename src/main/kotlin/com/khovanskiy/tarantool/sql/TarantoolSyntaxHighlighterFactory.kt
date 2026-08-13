package com.khovanskiy.tarantool.sql

import com.intellij.sql.dialects.base.SqlSyntaxHighlighterFactory

/**
 * Фабрика подсветки для языка TarantoolSQL.
 *
 * Обязательна: без собственной регистрации платформа резолвит подсветку
 * диалекта через общую SqlSyntaxHighlighterFactory, которая уходит
 * в бесконечную рекурсию — падают, например, диалоги источников данных.
 */
class TarantoolSyntaxHighlighterFactory : SqlSyntaxHighlighterFactory.Base(TarantoolSqlDialect.INSTANCE)
