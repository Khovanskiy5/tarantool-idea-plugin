package com.khovanskiy.tarantool.sql

import com.intellij.database.Dbms
import com.intellij.database.dialects.base.BaseDmlHelper

/**
 * Генерация DML для Tarantool.
 *
 * Tarantool SQL, как и SQLite, не понимает алиас таблицы в UPDATE:
 * стандартный генератор строил `UPDATE users t SET t.email = ...`,
 * и сервер отвечал «Syntax error near 't'» при сохранении из редактора
 * данных.
 */
class TarantoolDmlHelper(dbms: Dbms) : BaseDmlHelper(dbms) {

    override val needAliasInUpdateStatement: Boolean
        get() = false
}
