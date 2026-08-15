package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.sql.TarantoolClusterConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TarantoolClusterConfigTest {

    @Test
    @DisplayName("vshard-кластер: выбирается uri роутера и первый пользователь")
    fun parses_vshard_cluster_config() {
        // Сокращённый дословный config.yaml шаблона vshard_cluster (tt 2.14).
        val connection = TarantoolClusterConfig.parse(
            """
            credentials:
              users:
                client:
                  password: 'secret'
                  roles: [super]
                replicator:
                  password: 'secret'
                  roles: [replication]
                storage:
                  password: 'secret'
                  roles: [sharding]

            iproto:
              advertise:
                peer:
                  login: replicator

            groups:
              storages:
                replicasets:
                  storage-001:
                    instances:
                      storage-001-a:
                        iproto:
                          listen:
                            - uri: localhost:3301
                      storage-001-b:
                        iproto:
                          listen:
                            - uri: localhost:3302
              routers:
                replicasets:
                  router-001:
                    instances:
                      router-001-a:
                        iproto:
                          listen:
                            - uri: localhost:3305
            """.trimIndent().lines(),
        )

        assertEquals("localhost", connection.host)
        assertEquals(3305, connection.port, "для шардированного кластера подключаемся к роутеру")
        assertEquals("client", connection.user)
        assertEquals("secret", connection.password)
    }

    @Test
    @DisplayName("Одиночный инстанс: первый uri и учётные данные")
    fun parses_single_instance_config() {
        val connection = TarantoolClusterConfig.parse(
            """
            credentials:
              users:
                client:
                  password: 'secret'
                  roles: [super]

            iproto:
              listen:
                - uri: '127.0.0.1:3301'

            groups:
              group-001:
                replicasets:
                  replicaset-001:
                    instances:
                      instance-001: {}
            """.trimIndent().lines(),
        )

        assertEquals("127.0.0.1", connection.host)
        assertEquals(3301, connection.port)
        assertEquals("client", connection.user)
        assertEquals("secret", connection.password)
    }

    @Test
    @DisplayName("Пустой конфиг — безопасные умолчания")
    fun defaults_for_missing_config() {
        val connection = TarantoolClusterConfig.parse(emptyList())
        assertEquals("localhost", connection.host)
        assertEquals(3301, connection.port)
        assertEquals(null, connection.user)
    }

    @Test
    @DisplayName("Карта узлов: источники — роутеры и лидеры репликасетов")
    fun selects_router_and_replicaset_leaders() {
        val lines = """
            groups:
              storages:
                replicasets:
                  storage-001:
                    leader: storage-001-a
                    instances:
                      storage-001-a:
                        iproto:
                          listen:
                            - uri: localhost:3301
                      storage-001-b:
                        iproto:
                          listen:
                            - uri: localhost:3302
                  storage-002:
                    leader: storage-002-a
                    instances:
                      storage-002-a:
                        iproto:
                          listen:
                            - uri: localhost:3303
                      storage-002-b:
                        iproto:
                          listen:
                            - uri: localhost:3304
              routers:
                replicasets:
                  router-001:
                    instances:
                      router-001-a:
                        iproto:
                          listen:
                            - uri: localhost:3305
        """.trimIndent().lines()

        val nodes = TarantoolClusterConfig.parseNodes(lines)
        assertEquals(5, nodes.size, "все инстансы найдены")

        val selected = TarantoolClusterConfig.selectDataSourceNodes(nodes)
        assertEquals(
            listOf("storage-001-a" to 3301, "storage-002-a" to 3303, "router-001-a" to 3305),
            selected.map { it.name to it.port },
            "реплики RO пропущены, роутер и лидеры выбраны",
        )
    }

    @Test
    @DisplayName("точка входа: app.file читается, ключи вложенной app.cfg игнорируются")
    fun parses_app_file_ignoring_user_section() {
        val lines = """
            groups:
              storages:
                replicasets:
                  storage-001:
                    instances:
                      storage-001-a: {}

            app:
              file: 'src/app.lua'
              cfg:
                debugger:
                  enabled: true
                # пользовательская секция может содержать любые ключи,
                # включая такие же имена
                module: 'не точка входа'
        """.trimIndent().lines()

        val app = TarantoolClusterConfig.parseApp(lines)
        assertEquals("src/app.lua", app?.file)
        assertNull(app?.module, "module объявлен внутри app.cfg — это не точка входа")
    }

    @Test
    @DisplayName("точка входа: app.module без кавычек")
    fun parses_app_module() {
        val lines = """
            app:
              module: myapp.init
        """.trimIndent().lines()

        val app = TarantoolClusterConfig.parseApp(lines)
        assertEquals("myapp.init", app?.module)
        assertNull(app?.file)
    }

    @Test
    @DisplayName("точка входа: без секции app результат пустой")
    fun returns_null_without_app_section() {
        val lines = """
            credentials:
              users:
                client:
                  password: 'secret'
        """.trimIndent().lines()

        assertNull(TarantoolClusterConfig.parseApp(lines))
    }
}
