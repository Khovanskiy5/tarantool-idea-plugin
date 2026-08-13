package com.khovanskiy.tarantool.sql;

import com.intellij.sql.dialects.sql92.Sql92ReservedKeywords;

/**
 * Зарезервированные слова Tarantool SQL.
 *
 * Имя класса — часть конвенции {@code TokenClasses}: для {@code XxxTokens}
 * инфраструктура ищет {@code XxxReservedKeywords} и {@code XxxOptionalKeywords}
 * в том же пакете. Резерв Tarantool совпадает с SQL-92.
 */
public interface TarantoolReservedKeywords extends Sql92ReservedKeywords {
}
