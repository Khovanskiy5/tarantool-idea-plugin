package com.khovanskiy.tarantool.sql

import com.intellij.lexer.Lexer
import com.intellij.lexer.LookAheadLexer
import com.intellij.psi.tree.IElementType
import com.intellij.sql.dialects.sql92.Sql92Lexer
import com.intellij.sql.psi.SqlTokens

/**
 * Лексер диалекта: SQL-92 плюс сокрытие модификатора SEQSCAN.
 *
 * Грамматика SQL-92 не знает форму `FROM SEQSCAN t` и разбирала бы её
 * как таблицу SEQSCAN с алиасом t (а в JOIN-цепочке ломала бы разбор
 * целиком). Вместо правки грамматики слово перекрашивается в токен
 * [TarantoolOptionalKeywords.TNT_SEQSCAN], который
 * [TarantoolParserDefinition] объявляет «комментарием»: PsiBuilder
 * не показывает его парсеру, и грамматика видит чистое `FROM t`,
 * но в PSI-дереве слово остаётся листом-комментарием.
 *
 * Перекрашивается только голый идентификатор SEQSCAN, за которым
 * следует имя (обычное или в кавычках): одиночный `SELECT * FROM
 * seqscan` остаётся идентификатором и резолвится как таблица.
 */
class TarantoolLexer : LookAheadLexer(Sql92Lexer()) {

    override fun lookAhead(baseLexer: Lexer) {
        if (baseLexer.tokenType !== SqlTokens.SQL_IDENT ||
            !"SEQSCAN".contentEquals(baseLexer.tokenSequence, ignoreCase = true)
        ) {
            super.lookAhead(baseLexer)
            return
        }
        val seqscanEnd = baseLexer.tokenEnd
        baseLexer.advance()

        // Пробелы и комментарии между SEQSCAN и следующим значимым токеном
        // сохраняются со своими типами.
        val skipped = ArrayList<Pair<Int, IElementType>>()
        var next = baseLexer.tokenType
        while (next != null &&
            (SqlTokens.WS_TOKENS.contains(next) || SqlTokens.COMMENT_TOKENS.contains(next))
        ) {
            skipped.add(baseLexer.tokenEnd to next)
            baseLexer.advance()
            next = baseLexer.tokenType
        }

        val hide = next === SqlTokens.SQL_IDENT || next === SqlTokens.SQL_IDENT_DELIMITED
        addToken(seqscanEnd, if (hide) TarantoolParserDefinition.SEQSCAN_HIDDEN else SqlTokens.SQL_IDENT)
        skipped.forEach { (end, type) -> addToken(end, type) }
        // Следующий значимый токен обработает очередной вызов lookAhead.
    }
}
