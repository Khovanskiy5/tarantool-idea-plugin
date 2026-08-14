package com.khovanskiy.tarantool.schema

/**
 * Эвристика «этот YAML похож на кластерную конфигурацию Tarantool 3».
 *
 * Смотрит на ключи верхнего уровня (колонка 0 — вложенные ключи в YAML
 * всегда с отступом): разделы кластерной конфигурации достаточно
 * специфичны, чтобы одного совпадения хватало для вопроса пользователю.
 * Эвристика только показывает баннер — схему включает сам пользователь,
 * поэтому редкие ложные срабатывания стоят одного клика «скрыть».
 */
object TarantoolConfigHeuristics {

    private val TOP_LEVEL_KEYS = Regex(
        "^(groups|iproto|credentials|replication|database|wal|memtx|vinyl|snapshot|failover|sharding|security):",
        RegexOption.MULTILINE,
    )

    fun looksLikeClusterConfig(text: String): Boolean = TOP_LEVEL_KEYS.containsMatchIn(text)
}
