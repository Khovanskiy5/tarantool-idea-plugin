package com.khovanskiy.tarantool

import com.intellij.database.model.ObjectKind
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlLanguage
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.khovanskiy.tarantool.sql.TarantoolSqlDialect

/**
 * Отладочный стенд разбора диалекта TarantoolSQL: печатает PSI-деревья
 * проблемных конструкций из демо-файла queries.sql. Ассертов на структуру
 * нет намеренно — дампы читаются глазами при отладке резолва.
 */
class TarantoolSqlPsiDebugTest : BasePlatformTestCase() {

    private fun dump(title: String, sql: String) {
        val file = createLightFile("debug.sql", TarantoolSqlDialect.INSTANCE, sql)
        println("=== $title ===")
        println(sql)
        println(DebugUtil.psiToString(file, true, false))
    }

    fun `test dump seqscan select`() {
        dump("SEQSCAN одна таблица", """SELECT "name" FROM SEQSCAN "users" WHERE "city" = 'x';""")
    }

    fun `test dump seqscan join`() {
        dump(
            "SEQSCAN + JOIN с алиасами",
            """SELECT u."name", COUNT(*) FROM SEQSCAN "orders" o JOIN "users" u ON u."id" = o."user_id" GROUP BY u."name";""",
        )
    }

    fun `test dump set session`() {
        dump("SET SESSION", """SET SESSION "sql_seq_scan" = true;""")
    }

    fun `test set session setting resolve suppressed`() {
        val file = createLightFile(
            "debug.sql",
            TarantoolSqlDialect.INSTANCE,
            """SET SESSION "sql_seq_scan" = true;""",
        )
        val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)
            .single { it.name == "sql_seq_scan" }
        assertFalse(TarantoolSqlDialect.INSTANCE.shallResolve(reference, ObjectKind.COLUMN))
    }

    fun `test set statement suppresses resolve for values too`() {
        // Незакавыченное значение настройки тоже парсится «колонкой» —
        // резолв гасится для всего SET-статемента.
        val file = createLightFile(
            "debug.sql",
            TarantoolSqlDialect.INSTANCE,
            """SET SESSION "sql_default_engine" = memtx;""",
        )
        val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)
            .single { it.name == "memtx" }
        assertFalse(TarantoolSqlDialect.INSTANCE.shallResolve(reference, ObjectKind.COLUMN))
    }

    fun `test session setting outside set statement resolves`() {
        val file = createLightFile(
            "debug.sql",
            TarantoolSqlDialect.INSTANCE,
            """SELECT "sql_seq_scan" FROM "users";""",
        )
        val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)
            .single { it.name == "sql_seq_scan" }
        assertTrue(TarantoolSqlDialect.INSTANCE.shallResolve(reference, ObjectKind.COLUMN))
    }

    fun `test seqscan join parses without errors`() {
        val file = createLightFile(
            "debug.sql",
            TarantoolSqlDialect.INSTANCE,
            """SELECT u."name", COUNT(*) FROM SEQSCAN "orders" o JOIN "users" u ON u."id" = o."user_id" GROUP BY u."name";""",
        )
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).toList())
        val tables = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)
            .filter { it.text == "\"orders\"" || it.text == "\"users\"" }
        // Реальные таблицы — ссылки, а не алиасы при фиктивной таблице SEQSCAN.
        assertTrue(tables.isNotEmpty())
    }

    fun `test bare seqscan stays identifier`() {
        val file = createLightFile(
            "debug.sql",
            TarantoolSqlDialect.INSTANCE,
            """SELECT * FROM seqscan;""",
        )
        val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)
            .single { it.text.equals("seqscan", ignoreCase = true) }
        assertNotNull(reference)
    }

    fun `test builtin functions are known`() {
        val functions = TarantoolSqlDialect.INSTANCE.supportedFunctions
        for (name in listOf("COUNT", "SUM", "AVG", "MIN", "MAX", "TRIM", "TYPEOF", "NOW")) {
            assertNotNull("функция $name должна быть в реестре", functions.get(name))
        }
    }

    fun `test sql language registered`() {
        assertTrue(SqlLanguage.INSTANCE.id.isNotEmpty())
        assertEquals("Tarantool", TarantoolSqlDialect.INSTANCE.displayName)
    }
}
