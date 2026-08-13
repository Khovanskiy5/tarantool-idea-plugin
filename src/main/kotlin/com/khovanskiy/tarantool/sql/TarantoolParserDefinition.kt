package com.khovanskiy.tarantool.sql

import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.sql.dialects.base.SqlElementFactory
import com.intellij.sql.dialects.base.SqlElementFactoryBase
import com.intellij.sql.dialects.base.SqlParserDefinitionBase
import com.intellij.sql.dialects.sql92.Sql92Lexer
import com.intellij.sql.dialects.sql92.Sql92Parser
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

    override fun createLexer(project: Project?): Lexer = Sql92Lexer()

    override fun createParser(project: Project?): PsiParser = Sql92Parser()

    override fun getFileNodeType(): IFileElementType = FILE_TYPE

    companion object {
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
