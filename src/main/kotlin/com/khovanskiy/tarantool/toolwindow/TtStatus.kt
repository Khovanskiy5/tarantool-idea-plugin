package com.khovanskiy.tarantool.toolwindow

import com.google.gson.JsonParser

/** Строка панели: состояние одного инстанса. */
data class InstanceRow(
    val name: String,
    val status: String,
    val pid: String,
    val mode: String,
    val config: String,
    val box: String,
)

/** Разбор вывода `tt status -f json`. */
object TtStatus {

    fun parse(json: String): List<InstanceRow> {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) {
            return emptyList()
        }
        return root.asJsonObject.entrySet().map { (name, value) ->
            val fields = value.asJsonObject
            fun field(key: String): String =
                fields.get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            InstanceRow(
                name = name,
                status = field("status"),
                pid = field("pid"),
                mode = field("mode"),
                config = field("config"),
                box = field("box"),
            )
        }.sortedBy { it.name }
    }
}
