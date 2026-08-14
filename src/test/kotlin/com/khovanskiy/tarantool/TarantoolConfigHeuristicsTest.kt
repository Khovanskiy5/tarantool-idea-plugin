package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.schema.TarantoolConfigHeuristics
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TarantoolConfigHeuristicsTest {

    @Test
    @DisplayName("Кластерная конфигурация узнаётся по ключам верхнего уровня")
    fun recognizes_cluster_config() {
        val config = """
            credentials:
              users:
                admin:
                  password: 'secret'
            groups:
              group-001:
                replicasets:
                  replicaset-001:
                    instances:
                      instance-001: {}
        """.trimIndent()
        assertTrue(TarantoolConfigHeuristics.looksLikeClusterConfig(config))
    }

    @Test
    @DisplayName("Одного специфичного ключа достаточно")
    fun single_key_is_enough() {
        assertTrue(TarantoolConfigHeuristics.looksLikeClusterConfig("iproto:\n  listen:\n  - uri: '3301'\n"))
        assertTrue(TarantoolConfigHeuristics.looksLikeClusterConfig("sharding:\n  role: router\n"))
    }

    @Test
    @DisplayName("Чужие config.yaml не распознаются")
    fun rejects_foreign_configs() {
        // Типичный config.yaml другого стека: ключи не совпадают
        val foreign = """
            server:
              port: 8080
            logging:
              level: info
            database_url: postgres://localhost/db
        """.trimIndent()
        assertFalse(TarantoolConfigHeuristics.looksLikeClusterConfig(foreign))
    }

    @Test
    @DisplayName("Вложенные ключи с отступом не считаются верхнеуровневыми")
    fun ignores_nested_keys() {
        val nested = """
            services:
              tarantool:
                iproto: 3301
                credentials: none
        """.trimIndent()
        assertFalse(TarantoolConfigHeuristics.looksLikeClusterConfig(nested))
    }
}
