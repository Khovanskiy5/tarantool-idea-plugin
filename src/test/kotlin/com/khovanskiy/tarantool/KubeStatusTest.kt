package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.toolwindow.KubeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class KubeStatusTest {

    @Test
    @DisplayName("kubectl get pods -o json: имя, фаза, готовность, рестарты, узел")
    fun parses_pod_list() {
        // Сокращённый дословный вывод kubectl get pods -o json (k3s).
        val rows = KubeStatus.parse(
            """
            {
              "apiVersion": "v1",
              "kind": "List",
              "items": [
                {
                  "metadata": { "name": "tarantool-router-0", "namespace": "default" },
                  "spec": { "nodeName": "k3s-server-1" },
                  "status": {
                    "phase": "Running",
                    "containerStatuses": [
                      { "name": "tarantool", "ready": true, "restartCount": 2,
                        "state": { "running": { "startedAt": "2026-08-13T10:00:00Z" } } }
                    ]
                  }
                },
                {
                  "metadata": { "name": "tarantool-storage-0" },
                  "spec": { "nodeName": "k3s-agent-1" },
                  "status": {
                    "phase": "Pending",
                    "containerStatuses": [
                      { "name": "tarantool", "ready": false, "restartCount": 5,
                        "state": { "waiting": { "reason": "CrashLoopBackOff" } } }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, rows.size)
        val router = rows.first { it.name == "tarantool-router-0" }
        assertEquals("Running", router.status)
        assertEquals("1/1", router.pid)
        assertEquals("2", router.mode)
        assertEquals("k3s-server-1", router.config)

        // Причина ожидания контейнера информативнее фазы Pending.
        val storage = rows.first { it.name == "tarantool-storage-0" }
        assertEquals("CrashLoopBackOff", storage.status)
        assertEquals("0/1", storage.pid)
        assertEquals("5", storage.mode)
    }

    @Test
    @DisplayName("Удаляемый под показывается как Terminating")
    fun terminating_pod() {
        val rows = KubeStatus.parse(
            """
            { "items": [ {
              "metadata": { "name": "tarantool-router-0",
                            "deletionTimestamp": "2026-08-13T10:05:00Z" },
              "status": { "phase": "Running",
                          "containerStatuses": [ { "ready": true, "restartCount": 0,
                            "state": { "running": {} } } ] }
            } ] }
            """.trimIndent(),
        )
        assertEquals("Terminating", rows.single().status)
    }

    @Test
    @DisplayName("Пустой список и мусор на входе не роняют разбор")
    fun tolerates_garbage() {
        assertTrue(KubeStatus.parse("""{ "items": [] }""").isEmpty())
        assertTrue(KubeStatus.parse("""[1, 2, 3]""").isEmpty())
        assertTrue(KubeStatus.parse("""{ "items": [ { "spec": {} } ] }""").isEmpty())
    }
}
