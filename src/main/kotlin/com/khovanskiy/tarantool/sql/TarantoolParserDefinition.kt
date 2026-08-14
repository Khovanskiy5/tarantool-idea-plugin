package com.khovanskiy.tarantool.sql

import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.sql.dialects.base.SqlElementFactory
import com.intellij.sql.dialects.base.SqlElementFactoryBase
import com.intellij.sql.dialects.base.SqlParserDefinitionBase
import com.intellij.sql.dialects.sql92.Sql92Parser
import com.intellij.sql.psi.SqlTokens
import com.intellij.sql.psi.stubs.elementTypes.SqlFileElementType

/**
 * Фабрика PSI-элементов диалекта: базовые SQL-элементы.
 *
 * getStaticInfo обязателен: реализация по умолчанию бросает
 * UnsupportedOperationException, и любой разбор SQL нашего диалекта
 * (например, редактор таблицы источника данных) падал бы при построении
 * PSI. Info с дефолтными регистрациями покрывает всё, что порождает
 * SQL-92-парсер; для незарегистрированных типов у базы есть фолбэки.
 */
class TarantoolElementFactory : SqlElementFactory() {

    override fun getStaticInfo(): Info = INFO

    private companion object {
        val INFO: Info = Info().also(SqlElementFactory::getDefaultRegistrations)
    }
}

/**
 * Определение парсера языка TarantoolSQL.
 *
 * Синтаксис Tarantool SQL основан на SQL-92, поэтому лексер и парсер
 * переиспользуются от SQL-92; слова Tarantool (SEQSCAN, типы данных)
 * объявлены контекстными в [TarantoolOptionalKeywords], и парсер принимает
 * их как идентификаторы. Файл получает собственный тип узла, чтобы стабы
 * и индексы не смешивались с другими диалектами.
 */
class TarantoolParserDefinition : SqlParserDefinitionBase() {

    override fun createElementFactory(): SqlElementFactoryBase = TarantoolElementFactory()

    override fun createLexer(project: Project?): Lexer = TarantoolLexer()

    override fun createParser(project: Project?): PsiParser = Sql92Parser()

    override fun getFileNodeType(): IFileElementType = FILE_TYPE

    /**
     * TNT_SEQSCAN объявлен «комментарием»: PsiBuilder скрывает такие
     * токены от грамматики (SEQSCAN перед именем таблицы перекрашивает
     * [TarantoolLexer]), но лист остаётся в дереве. Именно комментарием,
     * а не whitespace: whitespace-листья переписывает форматтер.
     */
    override fun getCommentTokens(): TokenSet = COMMENT_TOKENS_WITH_SEQSCAN

    companion object {
        /**
         * Тип для спрятанного модификатора SEQSCAN. Собственный
         * зарегистрированный IElementType, а не TNT_SEQSCAN из реестра
         * SQL-токенов: типы ключевых слов SQL не регистрируются
         * в общем реестре элементов, и TokenSet их не принимает.
         */
        @JvmField
        val SEQSCAN_HIDDEN: IElementType = IElementType("TNT_SEQSCAN_HIDDEN", TarantoolSqlDialect.INSTANCE)

        private val COMMENT_TOKENS_WITH_SEQSCAN: TokenSet = TokenSet.orSet(
            SqlTokens.COMMENT_TOKENS,
            TokenSet.create(SEQSCAN_HIDDEN),
        )

        /**
         * Строго статическое поле: менеджер сериализации стабов собирает
         * файловые типы рефлексией по полям определения парсера при старте
         * индексирования — ленивoe разрешение языка здесь недопустимо.
         * Ссылка на INSTANCE диалекта гарантирует регистрацию языка
         * при загрузке класса.
         */
        @JvmField
        val FILE_TYPE: SqlFileElementType =
            SqlFileElementType("TARANTOOL_SQL_FILE", TarantoolSqlDialect.INSTANCE)
    }
}
