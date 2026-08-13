package com.khovanskiy.tarantool.sql;

/**
 * Токены SQL-диалекта Tarantool: SQL-92 плюс слова Tarantool.
 *
 * Java-интерфейс, а не Kotlin: {@code TokensHelper} собирает ключевые
 * слова рефлексией по публичным статическим полям, включая унаследованные.
 * По имени класса инфраструктура находит соседние
 * {@link TarantoolReservedKeywords} и {@link TarantoolOptionalKeywords}.
 */
public interface TarantoolTokens extends TarantoolReservedKeywords, TarantoolOptionalKeywords {
}
