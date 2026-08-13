package com.khovanskiy.tarantool.sql;

import com.intellij.sql.dialects.sql92.Sql92OptionalKeywords;
import com.intellij.sql.psi.SqlTokenType;
import com.intellij.sql.util.SqlTokenRegistry;

/**
 * Контекстные (не зарезервированные) слова Tarantool SQL сверх SQL-92 —
 * из extra/mkkeywordhash.c исходников Tarantool 3.8.
 *
 * Слова намеренно объявлены optional: парсер принимает их и как
 * идентификаторы, поэтому {@code SELECT * FROM SEQSCAN t} остаётся
 * синтаксически корректным.
 */
public interface TarantoolOptionalKeywords extends Sql92OptionalKeywords {

    /** Модификатор FROM: явное разрешение полного скана. */
    SqlTokenType TNT_SEQSCAN = SqlTokenRegistry.getType("SEQSCAN");

    SqlTokenType TNT_SHOW = SqlTokenRegistry.getType("SHOW");
    SqlTokenType TNT_ENGINE = SqlTokenRegistry.getType("ENGINE");
    SqlTokenType TNT_REPLACE = SqlTokenRegistry.getType("REPLACE");
    SqlTokenType TNT_RENAME = SqlTokenRegistry.getType("RENAME");
    SqlTokenType TNT_TRUNCATE = SqlTokenRegistry.getType("TRUNCATE");
    SqlTokenType TNT_AUTOINCREMENT = SqlTokenRegistry.getType("AUTOINCREMENT");
    SqlTokenType TNT_LIMIT = SqlTokenRegistry.getType("LIMIT");
    SqlTokenType TNT_OFFSET = SqlTokenRegistry.getType("OFFSET");
    SqlTokenType TNT_RECURSIVE = SqlTokenRegistry.getType("RECURSIVE");
    SqlTokenType TNT_IFNULL = SqlTokenRegistry.getType("IFNULL");

    // Типы данных Tarantool.
    SqlTokenType TNT_UNSIGNED = SqlTokenRegistry.getType("UNSIGNED");
    SqlTokenType TNT_STRING = SqlTokenRegistry.getType("STRING");
    SqlTokenType TNT_SCALAR = SqlTokenRegistry.getType("SCALAR");
    SqlTokenType TNT_VARBINARY = SqlTokenRegistry.getType("VARBINARY");
    SqlTokenType TNT_UUID = SqlTokenRegistry.getType("UUID");
    SqlTokenType TNT_DATETIME = SqlTokenRegistry.getType("DATETIME");
    SqlTokenType TNT_MAP = SqlTokenRegistry.getType("MAP");
    SqlTokenType TNT_ARRAY = SqlTokenRegistry.getType("ARRAY");
}
