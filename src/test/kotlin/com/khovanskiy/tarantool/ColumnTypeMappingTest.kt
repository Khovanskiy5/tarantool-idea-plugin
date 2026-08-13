package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.jdbcshim.ColumnTypeMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types

/**
 * Честные JDBC-типы в метаданных колонок.
 *
 * Родной SQLDatabaseMetadata.getColumns() коннектора 1.9.4 отдаёт
 * DATA_TYPE = Types.OTHER для всех колонок, а настоящий тип — только
 * строкой TYPE_NAME («unsigned»). Редактор данных IDE подбирает домен
 * по имени типа, «unsigned» не узнаёт и квотирует числа строками:
 * SELECT ... WHERE id = '2' → «Type mismatch: can not convert
 * string('2') to unsigned». Обёртка шима переписывает обе колонки.
 *
 * Фейк повторяет повадки SQLResultSet коннектора: чтение значения до
 * next() бросает «Cursor is out of range», каждое чтение перезаписывает
 * флаг wasNull — на этих повадках обёртка уже ловилась.
 */
class ColumnTypeMappingTest {

    // --- словарь соответствия типов формата спейса ---

    @Test
    fun `числовые типы формата получают числовые JDBC-типы`() {
        assertEquals(Types.BIGINT, ColumnTypeMapping.jdbcTypeFor("unsigned"))
        assertEquals(Types.BIGINT, ColumnTypeMapping.jdbcTypeFor("integer"))
        assertEquals(Types.DOUBLE, ColumnTypeMapping.jdbcTypeFor("number"))
        assertEquals(Types.DOUBLE, ColumnTypeMapping.jdbcTypeFor("double"))
        assertEquals(Types.DECIMAL, ColumnTypeMapping.jdbcTypeFor("decimal"))
    }

    @Test
    fun `имена согласованы со словарём SQL-метаданных выборок`() {
        // так эти же типы называет SqlProtoUtils.getSQLMetadata в выборках
        assertEquals("integer", ColumnTypeMapping.typeNameFor("unsigned"))
        assertEquals("integer", ColumnTypeMapping.typeNameFor("integer"))
        assertEquals("double", ColumnTypeMapping.typeNameFor("number"))
        assertEquals("string", ColumnTypeMapping.typeNameFor("string"))
        assertEquals("datetime", ColumnTypeMapping.typeNameFor("datetime"))
    }

    @Test
    fun `остальные типы формата отображаются без сюрпризов`() {
        assertEquals(Types.VARCHAR, ColumnTypeMapping.jdbcTypeFor("string"))
        assertEquals(Types.BOOLEAN, ColumnTypeMapping.jdbcTypeFor("boolean"))
        assertEquals(Types.VARBINARY, ColumnTypeMapping.jdbcTypeFor("varbinary"))
        assertEquals(Types.TIMESTAMP, ColumnTypeMapping.jdbcTypeFor("datetime"))
        assertEquals(Types.OTHER, ColumnTypeMapping.jdbcTypeFor("uuid"))
    }

    @Test
    fun `тип вне словаря остаётся как есть с DATA_TYPE = OTHER`() {
        assertEquals(Types.OTHER, ColumnTypeMapping.jdbcTypeFor("map"))
        assertEquals("map", ColumnTypeMapping.typeNameFor("map"))
        assertEquals(Types.OTHER, ColumnTypeMapping.jdbcTypeFor("interval"))
        assertEquals("interval", ColumnTypeMapping.typeNameFor("interval"))
    }

    @Test
    fun `регистр имени типа не важен, null не роняет`() {
        assertEquals(Types.BIGINT, ColumnTypeMapping.jdbcTypeFor("UNSIGNED"))
        assertEquals(Types.OTHER, ColumnTypeMapping.jdbcTypeFor(null))
        assertNull(ColumnTypeMapping.typeNameFor(null))
    }

    // --- обёртка выдачи getColumns ---

    @Test
    fun `DATA_TYPE перечитывается по TYPE_NAME текущей строки`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        wrapped.next()
        assertEquals(Types.BIGINT, wrapped.getInt(5))
        assertEquals(Types.BIGINT, wrapped.getInt("DATA_TYPE"))
        assertEquals(Types.BIGINT.toLong(), wrapped.getLong("data_type"))
        assertEquals(Types.BIGINT, wrapped.getObject(5))
        assertEquals(Types.BIGINT.toString(), wrapped.getString(5))
    }

    @Test
    fun `все числовые аксессоры DATA_TYPE согласованы`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        wrapped.next()
        assertEquals(Types.BIGINT.toShort(), wrapped.getShort("DATA_TYPE"))
        assertEquals(Types.BIGINT.toFloat(), wrapped.getFloat(5))
        assertEquals(Types.BIGINT.toDouble(), wrapped.getDouble(5))
        assertEquals(BigDecimal.valueOf(Types.BIGINT.toLong()), wrapped.getBigDecimal(5))
        assertEquals(Types.BIGINT.toString(), wrapped.getNString(5))
    }

    @Test
    fun `двухаргументный getObject типизирован честно`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        wrapped.next()
        assertEquals(Types.BIGINT, wrapped.getObject(5, Int::class.javaObjectType))
        assertEquals(Types.BIGINT, wrapped.getObject("DATA_TYPE", Number::class.java))
        assertEquals(Types.BIGINT.toString(), wrapped.getObject(5, String::class.java))
        assertEquals("integer", wrapped.getObject(6, String::class.java))
        assertEquals("integer", wrapped.getObject("TYPE_NAME", CharSequence::class.java))
    }

    @Test
    fun `TYPE_NAME переводится в имя из словаря выборок`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        wrapped.next()
        assertEquals("integer", wrapped.getString(6))
        assertEquals("integer", wrapped.getString("TYPE_NAME"))
        assertEquals("integer", wrapped.getString("type_name"))
        assertEquals("integer", wrapped.getNString(6))
        assertEquals("integer", wrapped.getObject(6))
    }

    @Test
    fun `неизвестный тип проходит сквозь обёртку нетронутым`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("map"))
        wrapped.next()
        assertEquals(Types.OTHER, wrapped.getInt("DATA_TYPE"))
        assertEquals("map", wrapped.getString("TYPE_NAME"))
    }

    @Test
    fun `остальные колонки и методы идут в делегат`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        assertTrue(wrapped.next())
        assertEquals("id", wrapped.getString(4))
        assertEquals("id", wrapped.getString("COLUMN_NAME"))
    }

    @Test
    fun `findColumn и setFetchSize с аргументом колонки не читают строку`() {
        // классический паттерн клиентов: кеширование индексов ДО первого next();
        // жадное скрытое чтение TYPE_NAME здесь бросало бы Cursor is out of range
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        assertEquals(5, wrapped.findColumn("DATA_TYPE"))
        assertEquals(6, wrapped.findColumn("TYPE_NAME"))
        wrapped.fetchSize = 5
    }

    @Test
    fun `wasNull после чтения DATA_TYPE отражает саму колонку`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        wrapped.next()
        assertEquals(Types.BIGINT, wrapped.getInt(5))
        assertFalse(wrapped.wasNull())

        // колонка без type в формате: TYPE_NAME = NULL, но DATA_TYPE в тупле
        // не NULL — wasNull не должен наследоваться от скрытого чтения
        val untyped = ColumnTypeMapping.wrapColumns(fakeColumns(null))
        untyped.next()
        assertEquals(Types.OTHER, untyped.getInt(5))
        assertFalse(untyped.wasNull())
    }

    @Test
    fun `wasNull чужой колонки не затирается служебными вызовами`() {
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        wrapped.next()
        assertNull(wrapped.getString(1)) // TABLE_CAT = NULL
        assertTrue(wrapped.wasNull())
        wrapped.findColumn("DATA_TYPE")
        assertTrue(wrapped.wasNull())
    }

    @Test
    fun `getStatement метаданных не отдаёт сырое соединение`() {
        // по JDBC ResultSet из DatabaseMetaData вправе вернуть null; делегат
        // вернул бы Statement сырого соединения в обход эмуляции автокоммита
        val wrapped = ColumnTypeMapping.wrapColumns(fakeColumns("unsigned"))
        assertNull(wrapped.statement)
    }

    // --- обёртка DatabaseMetaData и проводка через ShimDriver ---

    @Test
    fun `getColumns у обёрнутых метаданных отдаёт честные типы`() {
        val connection = fake(Connection::class.java) { _, _ -> null }
        val metadata = fake(DatabaseMetaData::class.java) { method, _ ->
            if (method == "getColumns") fakeColumns("unsigned") else null
        }
        val wrapped = ColumnTypeMapping.wrap(metadata, connection)
        val columns = wrapped.getColumns(null, null, "users", null)
        columns.next()
        assertEquals(Types.BIGINT, columns.getInt("DATA_TYPE"))
        assertEquals("integer", columns.getString("TYPE_NAME"))
    }

    @Test
    fun `getConnection возвращает обёрнутое соединение, а не сырое`() {
        val connection = fake(Connection::class.java) { _, _ -> null }
        val metadata = fake(DatabaseMetaData::class.java) { _, _ -> null }
        assertSame(connection, ColumnTypeMapping.wrap(metadata, connection).getConnection())
    }

    @Test
    fun `getMetaData соединения-прокси отдаёт обёрнутые метаданные`() {
        // единственное место, где обёртка включается, — case "getMetaData"
        // в AutoCommitEmulation; проверяем проводку через реальный хендлер
        val metadata = fake(DatabaseMetaData::class.java) { method, _ ->
            if (method == "getColumns") fakeColumns("unsigned") else null
        }
        val rawConnection = fake(Connection::class.java) { method, _ ->
            if (method == "getMetaData") metadata else null
        }
        val handlerClass = Class.forName("com.khovanskiy.tarantool.jdbcshim.ShimDriver\$AutoCommitEmulation")
        val constructor = handlerClass.getDeclaredConstructor(Connection::class.java)
        constructor.isAccessible = true
        val proxyConnection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            constructor.newInstance(rawConnection) as InvocationHandler,
        ) as Connection

        val wrappedMetadata = proxyConnection.metaData
        assertSame(proxyConnection, wrappedMetadata.connection)
        val columns = wrappedMetadata.getColumns(null, null, "users", null)
        columns.next()
        assertEquals(Types.BIGINT, columns.getInt("DATA_TYPE"))
    }

    // --- фейки: строка getColumns из users для колонки id ---

    /**
     * Повторяет повадки SQLResultSet коннектора 1.9.4: значения читаются
     * только после next(), каждое чтение перезаписывает флаг wasNull,
     * getStatement отдаёт Statement (у делегата он от сырого соединения).
     */
    private fun fakeColumns(typeName: String?): ResultSet {
        val byIndex = mapOf(1 to null, 4 to "id", 5 to Types.OTHER, 6 to typeName)
        val labels = mapOf("TABLE_CAT" to 1, "COLUMN_NAME" to 4, "DATA_TYPE" to 5, "TYPE_NAME" to 6)
        var positioned = false
        var lastWasNull = false

        fun cell(args: Array<Any?>?): Any? {
            val index = when (val key = args?.firstOrNull()) {
                is Int -> key
                is String -> labels[key.uppercase()] ?: throw SQLException("no column $key")
                else -> throw SQLException("no column argument")
            }
            if (!positioned) {
                throw SQLException("Cursor is out of range. Try to call next() or previous() before.")
            }
            val value = byIndex[index]
            lastWasNull = value == null
            return value
        }

        return fake(ResultSet::class.java) { method, args ->
            when (method) {
                "next" -> {
                    positioned = true
                    true
                }
                "wasNull" -> lastWasNull
                "findColumn" -> {
                    val label = args?.first() as String
                    labels[label.uppercase()] ?: throw SQLException("no column $label")
                }
                "getString", "getNString" -> cell(args)?.toString()
                "getInt" -> cell(args) as? Int ?: 0
                "getLong" -> (cell(args) as? Int)?.toLong() ?: 0L
                "getShort" -> (cell(args) as? Int)?.toShort() ?: 0.toShort()
                "getFloat" -> (cell(args) as? Int)?.toFloat() ?: 0f
                "getDouble" -> (cell(args) as? Int)?.toDouble() ?: 0.0
                "getBigDecimal" -> (cell(args) as? Int)?.let { BigDecimal.valueOf(it.toLong()) }
                "getObject" -> cell(args)
                "getStatement" -> fake(Statement::class.java) { _, _ -> null }
                else -> null
            }
        }
    }

    private fun <T> fake(type: Class<T>, handler: (String, Array<Any?>?) -> Any?): T {
        val instance = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args) ?: defaultFor(method.returnType)
        }
        return type.cast(instance)
    }

    /** Значение по умолчанию для примитивного возвращаемого типа прокси. */
    private fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }
}
