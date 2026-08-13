package com.khovanskiy.tarantool.sql

import java.io.File

/**
 * Извлечение параметров подключения из config.yaml кластера Tarantool.
 *
 * Разбор строковый и намеренно терпимый: YAML-парсера в зависимостях нет,
 * а нужны только адрес, порт и учётные данные. Для шардированных кластеров
 * предпочитается uri из группы роутеров — подключаться к стореджу напрямую
 * бессмысленно.
 */
object TarantoolClusterConfig {

    data class Connection(
        val host: String,
        val port: Int,
        val user: String?,
        val password: String?,
    )

    /** Инстанс кластера с адресом из config.yaml. */
    data class Node(
        val name: String,
        val host: String,
        val port: Int,
        val router: Boolean,
        val leader: Boolean,
    )

    /**
     * Ищет config.yaml окружения: сначала в корне, затем в каталогах
     * приложений первого уровня (кластерные шаблоны tt кладут конфиг
     * внутрь приложения).
     */
    fun locate(basePath: File): File? {
        for (name in CONFIG_NAMES) {
            val root = File(basePath, name)
            if (root.isFile) {
                return root
            }
        }
        val children = basePath.listFiles { file ->
            file.isDirectory && file.name !in SERVICE_DIRS && !file.name.startsWith(".")
        } ?: return null
        for (child in children.sortedBy { it.name }) {
            for (name in CONFIG_NAMES) {
                val nested = File(child, name)
                if (nested.isFile) {
                    return nested
                }
            }
        }
        return null
    }

    fun parse(lines: List<String>): Connection {
        var host = DEFAULT_HOST
        var port = DEFAULT_PORT
        var routerHost: String? = null
        var routerPort: Int? = null
        var firstUriSeen = false
        var inRouterZone = false
        var user: String? = null
        var password: String? = null
        var inUsers = false
        var pendingUser: String? = null

        for (raw in lines) {
            val line = raw.substringBefore('#')

            // Зона роутеров: от заголовка с "router" до следующего заголовка
            // того же уровня вложенности групп.
            GROUP_HEADER.find(line)?.let { header ->
                inRouterZone = header.groupValues[1].contains("router", ignoreCase = true)
            }

            URI_PATTERN.find(line)?.let {
                val uriHost = it.groupValues[1].ifBlank { DEFAULT_HOST }
                val uriPort = it.groupValues[2].toInt()
                if (!firstUriSeen) {
                    firstUriSeen = true
                    host = uriHost
                    port = uriPort
                }
                if (inRouterZone && routerPort == null) {
                    routerHost = uriHost
                    routerPort = uriPort
                }
            }

            when {
                line.trim() == "users:" -> inUsers = true
                inUsers && USER_NAME_PATTERN.matches(line) ->
                    pendingUser = USER_NAME_PATTERN.find(line)!!.groupValues[1]
                inUsers && pendingUser != null && PASSWORD_PATTERN.find(line) != null -> {
                    if (user == null) {
                        user = pendingUser
                        password = PASSWORD_PATTERN.find(line)!!.groupValues[1]
                    }
                }
                line.isNotBlank() && !line.startsWith(" ") -> inUsers = false
            }
        }

        return Connection(routerHost ?: host, routerPort ?: port, user, password)
    }

    /**
     * Полная карта узлов кластера: каждый uri привязывается к последнему
     * незарезервированному заголовку (имени инстанса); лидерство — из
     * объявлений `leader:` репликасетов.
     */
    fun parseNodes(lines: List<String>): List<Node> {
        val leaders = mutableSetOf<String>()
        data class Raw(val name: String, val host: String, val port: Int, val router: Boolean)
        val raw = mutableListOf<Raw>()
        var inRouterZone = false
        var pendingInstance: String? = null

        for (line in lines.map { it.substringBefore('#') }) {
            GROUP_HEADER.find(line)?.let { header ->
                inRouterZone = header.groupValues[1].contains("router", ignoreCase = true)
            }
            LEADER_PATTERN.find(line)?.let { leaders += it.groupValues[1] }
            INSTANCE_HEADER.find(line)?.let { header ->
                val name = header.groupValues[1]
                if (name !in RESERVED_KEYS) {
                    pendingInstance = name
                }
            }
            URI_PATTERN.find(line)?.let {
                raw += Raw(
                    name = pendingInstance ?: "instance",
                    host = it.groupValues[1].ifBlank { DEFAULT_HOST },
                    port = it.groupValues[2].toInt(),
                    router = inRouterZone,
                )
            }
        }

        return raw.map { node ->
            Node(
                name = node.name,
                host = node.host,
                port = node.port,
                router = node.router,
                leader = node.router || leaders.isEmpty() || node.name in leaders,
            )
        }
    }

    /**
     * Узлы, для которых имеет смысл источник данных: роутеры (точка входа
     * приложения) и лидеры сторедж-репликасетов (там данные и запись).
     */
    fun selectDataSourceNodes(nodes: List<Node>): List<Node> =
        nodes.filter { it.router || it.leader }

    private const val DEFAULT_HOST = "localhost"
    private const val DEFAULT_PORT = 3301

    private val CONFIG_NAMES = listOf("config.yaml", "config.yml")
    private val SERVICE_DIRS = setOf("var", "bin", "include", "distfiles", "modules", "templates", "instances.enabled")

    private val GROUP_HEADER = Regex("""^ {2}([\w-]+):\s*$""")
    private val URI_PATTERN = Regex("""uri:\s*'?(?:([\w.\-]+):)?(\d{2,5})'?""")
    private val USER_NAME_PATTERN = Regex("""^\s{4,8}([\w\-]+):\s*$""")
    private val PASSWORD_PATTERN = Regex("""password:\s*'([^']*)'""")
    private val LEADER_PATTERN = Regex("""leader:\s*'?([\w\-]+)'?""")
    private val INSTANCE_HEADER = Regex("""^\s{6,12}([\w\-]+):\s*(\{\s*}\s*)?$""")

    /** Служебные ключи YAML, которые не являются именами инстансов. */
    private val RESERVED_KEYS = setOf(
        "instances", "iproto", "listen", "advertise", "app", "roles",
        "replication", "sharding", "peer", "credentials", "users", "groups",
        "replicasets", "memtx", "database", "config", "labels", "log",
        "roles_cfg", "snapshot", "wal",
    )
}
