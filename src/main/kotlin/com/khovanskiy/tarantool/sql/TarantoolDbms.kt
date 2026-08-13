package com.khovanskiy.tarantool.sql

import com.intellij.database.Dbms
import com.intellij.openapi.util.IconLoader

/**
 * СУБД Tarantool в реестре Database-инструментов.
 *
 * Инстанс регистрируется расширением `com.intellij.database.dbms`
 * (атрибут instance ссылается на статическое поле этого класса)
 * и связывает SQL-диалект с источниками данных.
 */
object TarantoolDbms {

    /**
     * Без паттерна автоопределения — намеренно: паттерн матчится на имя
     * встроенного драйвера Tarantool и URL jdbc:tarantool://, из-за чего
     * источники данных получали бы нашу СУБД вместо UNKNOWN и теряли
     * интроспекцию и jdbcHelper, зарегистрированные для UNKNOWN.
     * Этот Dbms — только якорь SQL-диалекта, выбираемого вручную.
     */
    @JvmField
    val TARANTOOL: Dbms = Dbms.create(
        "TARANTOOL_CUSTOM",
        "Tarantool (dialect)",
        { IconLoader.getIcon("/icons/tarantool.svg", TarantoolDbms::class.java) },
    )
}
