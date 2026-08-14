package com.khovanskiy.tarantool.sql

import com.intellij.database.Dbms
import com.intellij.database.model.ObjectKind
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.base.SqlLanguageDialectBase
import com.intellij.sql.dialects.base.TokensHelper
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.sql.psi.SqlSetStatement
import com.intellij.sql.psi.SqlStatement

/**
 * SQL-диалект Tarantool.
 *
 * Синтаксис Tarantool близок к SQL-92, поэтому лексика и парсинг берутся
 * от SQL-92 (см. [TarantoolParserDefinition]), а поверх добавляются слова
 * Tarantool ([TarantoolTokens]: SEQSCAN, типы данных) и системные
 * переменные для SET SESSION.
 *
 * Подсветка синтаксических ошибок отключена, как у GenericSQL: полной
 * грамматики Tarantool у парсера нет, и конструкции вида
 * `SELECT * FROM SEQSCAN t` не должны краснить редактор.
 */
class TarantoolSqlDialect private constructor() : SqlLanguageDialectBase("TarantoolSQL") {

    override fun createTokensHelper(): TokensHelper = createTokensHelper(TarantoolTokens::class.java)

    override fun getDbms(): Dbms = TarantoolDbms.TARANTOOL

    override fun getDisplayName(): String = "Tarantool"

    override fun supportsErrorHighlighting(): Boolean = false

    override fun isOperatorSupported(operator: IElementType): Boolean = true

    /**
     * SEQSCAN в позиции таблицы — модификатор, а не ссылка: парсер SQL-92
     * о нём не знает и разбирает `FROM SEQSCAN t` как таблицу с алиасом.
     * Резолв такой «таблицы» подавляется, чтобы не было ложного
     * «unresolved table SEQSCAN».
     */
    override fun shallResolve(expression: SqlReferenceExpression?, kind: ObjectKind): Boolean {
        if (expression != null && expression.text.equals("SEQSCAN", ignoreCase = true)) {
            return false
        }
        if (expression != null && isInsideSetStatement(expression)) {
            return false
        }
        return super.shallResolve(expression, kind)
    }

    /**
     * `SET SESSION "имя" = значение` парсер SQL-92 не знает (после SESSION
     * он ждёт AUTHORIZATION), поэтому и имя настройки, и незакавыченное
     * значение (`= memtx`) остаются «колонками» внутри SET-статемента —
     * без подавления резолва оба подсвечивались бы «unable to resolve
     * column». Настоящих колонок внутри SET в Tarantool не бывает, поэтому
     * резолв гасится для всего статемента; квалифицированные ссылки
     * (подзапросов в SET тоже не бывает) не трогаются.
     */
    private fun isInsideSetStatement(expression: SqlReferenceExpression): Boolean {
        if (expression.qualifierExpression != null) {
            return false
        }
        return PsiTreeUtil.getParentOfType(expression, SqlStatement::class.java) is SqlSetStatement
    }

    override fun getSystemVariables(): Set<String> = Constants.SESSION_SETTINGS

    companion object {
        /**
         * Расширение sql.dialect получает диалект рефлексией по статическому
         * полю INSTANCE (см. DbmsExtension.InstanceBean) — конструктором
         * оно не пользуется. Язык обязан быть синглтоном.
         */
        @JvmField
        val INSTANCE = TarantoolSqlDialect()
    }

    private object Constants {
        /**
         * Настройки сессии для `SET SESSION "имя" = значение` —
         * из src/box/session_settings.c исходников Tarantool 3.8.
         */
        val SESSION_SETTINGS = setOf(
            "sql_default_engine",
            "sql_full_column_names",
            "sql_full_metadata",
            "sql_parser_debug",
            "sql_recursive_triggers",
            "sql_reverse_unordered_selects",
            "sql_select_debug",
            "sql_seq_scan",
            "sql_vdbe_debug",
        )
    }
}
