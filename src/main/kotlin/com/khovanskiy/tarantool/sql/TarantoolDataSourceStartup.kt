package com.khovanskiy.tarantool.sql

import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.DatabaseDriverImpl
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dataSource.url.template.UrlTemplate
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.ui.classpath.SimpleClasspathElementFactory
import com.khovanskiy.tarantool.TarantoolBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Автонастройка Database-интеграции для tt-проекта.
 *
 * При открытии проекта с tt.yaml:
 *  1) регистрирует пользовательский драйвер «Tarantool (shim)» — единственный
 *     jar шима, поставляемый плагином: внутрь шейднут коннектор с форком
 *     MsgPackLite (поддержка ext-типов: datetime, uuid, decimal, interval),
 *     а сам шим эмулирует автокоммит (без него сохранение из редактора
 *     данных падает);
 *  2) создаёт источник данных из config.yaml (адрес, порт, учётные данные),
 *     если ни одного Tarantool-источника в проекте ещё нет.
 *
 * Повторные запуски ничего не дублируют.
 */
class TarantoolDataSourceStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return
        if (!File(basePath, "tt.yaml").isFile) {
            // Реестр драйверов общий для всей IDE, а починка конфигурации
            // (вычистка артефакта коннектора, актуальный jar шима) до сих пор
            // выполнялась только в tt-проектах: пользователь с источником,
            // созданным вручную в обычном проекте, ждал бы починки до первого
            // открытия какого-нибудь tt-проекта. Уже существующий шим-драйвер
            // чиним из любого проекта; новый не регистрируем.
            withContext(Dispatchers.EDT) {
                val manager = DatabaseDriverManager.getInstance()
                if (manager.drivers.any { it.driverClass == SHIM_DRIVER_CLASS }) {
                    ensureShimDriver()
                }
            }
            return
        }

        val configFile = TarantoolClusterConfig.locate(File(basePath))
        val lines = configFile?.let { runCatching { it.readLines() }.getOrNull() }.orEmpty()
        val connection = TarantoolClusterConfig.parse(lines)
        val nodes = TarantoolClusterConfig.selectDataSourceNodes(TarantoolClusterConfig.parseNodes(lines))

        withContext(Dispatchers.EDT) {
            val driver = ensureShimDriver() ?: return@withContext
            ensureDataSources(project, driver, connection, nodes)
        }
    }

    /**
     * Приводит реестр драйверов к эталону и возвращает шим-драйвер.
     *
     * Метод восстанавливает конфигурацию после любых прошлых аварий:
     * убирает дубли шим-драйвера (идентификация по классу, а не по id —
     * id в реестре генерируется), выправляет classpath на актуальный jar
     * плагина (путь меняется при обновлениях) и вычищает битые ссылки
     * на прошлые версии шима из встроенного драйвера.
     */
    private fun ensureShimDriver(): DatabaseDriver? {
        val manager = DatabaseDriverManager.getInstance()
        val shimJar = findShimJar() ?: return null
        val shimClasspath = SimpleClasspathElementFactory.createElements(VfsUtilCore.pathToUrl(shimJar.path))

        val builtin = manager.getDriver(BUILTIN_DRIVER_ID)

        // Ручные эксперименты могли оставить во встроенном драйвере ссылки
        // на несуществующие версии шима — они ломают и его.
        builtin?.let { cleanStaleShimReferences(manager, it) }

        val shims = manager.drivers.filter { it.driverClass == SHIM_DRIVER_CLASS }
        val driver = shims.firstOrNull() ?: run {
            val template = (builtin as? DatabaseDriverImpl)?.urlTemplates?.firstOrNull()
                ?: UrlTemplate("default", "jdbc:tarantool://{host::localhost}:{port::3301}")
            manager.createDriver("Tarantool (shim)", SHIM_DRIVER_ID, template)
        }

        (driver as? DatabaseDriverImpl)?.apply {
            setName("Tarantool (shim)")
            setDriverClass(SHIM_DRIVER_CLASS)
            // Диалект драйвера обязан совпадать с диалектом forcedDbms,
            // иначе консоль источника показывает баннер «Inconsistent
            // language» с рекомендацией сменить Generic SQL на Tarantool.
            setSqlDialect("TarantoolSQL")
            if (artifacts.isNotEmpty()) {
                // Коннектор шейдится в jar шима вместе с форком MsgPackLite
                // (поддержка ext-типов); артефакт с Maven Central в classpath
                // перекрывал бы форк оригинальным классом — вычищаем, в том
                // числе унаследованный конфигурациями прошлых версий.
                setArtifacts(emptyList())
            }
            setAdditionalClasspathElements(shimClasspath)
            // Новые источники этого драйвера сразу в автокоммите.
            setOption(DatabaseDriver.OPTION_AUTO_COMMIT, true)
            // Источники получают нашу СУБД: она включает генерацию DML
            // без алиасов в UPDATE (Tarantool их не понимает) и SQL-диалект
            // Tarantool; остальные подсистемы фолбэчатся на generic.
            setForcedDbms(TarantoolDbms.TARANTOOL)
        }
        manager.updateDriver(driver)

        // Дубли от прошлых запусков удаляются после выбора основного.
        shims.drop(1).forEach(manager::removeDriver)
        return driver
    }

    /** Убирает из classpath драйвера ссылки на файлы шима прошлых версий. */
    private fun cleanStaleShimReferences(manager: DatabaseDriverManager, driver: DatabaseDriver) {
        val cleaned = driver.additionalClasspathElements.filterNot { element ->
            element.classesRootUrls.any { it.contains("tarantool-jdbc-shim") }
        }
        if (cleaned.size != driver.additionalClasspathElements.size) {
            driver.setAdditionalClasspathElements(cleaned)
            manager.updateDriver(driver)
        }
    }

    private fun ensureDataSources(
        project: Project,
        driver: DatabaseDriver,
        connection: TarantoolClusterConfig.Connection,
        nodes: List<TarantoolClusterConfig.Node>,
    ) {
        val manager = LocalDataSourceManager.getInstance(project)

        // Существующие источники чинятся идемпотентно: актуальный драйвер,
        // автокоммит и наша СУБД (она кэшируется в info источника
        // и из forcedDbms драйвера не пересчитывается).
        for (dataSource in manager.dataSources.filter { it.url?.startsWith("jdbc:tarantool:") == true }) {
            if (dataSource.databaseDriver?.driverClass != SHIM_DRIVER_CLASS) {
                dataSource.setDatabaseDriver(driver)
                dataSource.setAutoCommit(true)
            }
            if (dataSource.dbms != TarantoolDbms.TARANTOOL) {
                dataSource.info.setDbms(TarantoolDbms.TARANTOOL)
            }
        }

        // Недостающие источники: роутеры и лидеры репликасетов; одиночный
        // инстанс представлен единственным узлом-«лидером».
        val targets = nodes.ifEmpty {
            listOf(TarantoolClusterConfig.Node("instance", connection.host, connection.port, router = false, leader = true))
        }
        val query = connection.user?.let { user ->
            "?user=$user" + (connection.password?.let { "&password=$it" } ?: "")
        }.orEmpty()

        var created = 0
        for (node in targets) {
            val address = "//" + node.host + ":" + node.port
            val exists = manager.dataSources.any { it.url?.contains(address) == true }
            if (exists) {
                continue
            }
            val suffix = if (targets.size > 1) "-" + node.name else ""
            val dataSource = LocalDataSource.create(
                project.name + suffix + "@" + node.port,
                SHIM_DRIVER_CLASS,
                "jdbc:tarantool:" + address + query,
                connection.user,
            )
            dataSource.setDatabaseDriver(driver)
            dataSource.setAutoCommit(true)
            dataSource.info.setDbms(TarantoolDbms.TARANTOOL)
            manager.addDataSource(dataSource)
            created++
        }

        if (created > 0) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Tarantool")
                .createNotification(
                    TarantoolBundle.message("startup.datasources.created", created),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }

    /** Путь к jar шима внутри установленного плагина. */
    private fun findShimJar(): File? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.khovanskiy.tarantool")) ?: return null
        val driverDir = plugin.pluginPath.resolve("driver").toFile()
        return driverDir.listFiles { file -> file.extension == "jar" }?.firstOrNull()
    }

    private companion object {
        const val BUILTIN_DRIVER_ID = "tarantool"
        const val SHIM_DRIVER_ID = "tarantool-shim"
        const val SHIM_DRIVER_CLASS = "com.khovanskiy.tarantool.jdbcshim.ShimDriver"

    }
}
