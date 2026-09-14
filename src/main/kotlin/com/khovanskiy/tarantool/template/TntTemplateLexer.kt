package com.khovanskiy.tarantool.template

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Лексер шаблона: делит текст на HTML и вкрапления.
 *
 * Правила те же, что у переводчика tnt-template: `{{ }}` — вывод,
 * `{!! !!}` — сырой вывод, `{{-- --}}` — заметка, `@слово` — директива,
 * за которой сразу или через пробелы могут идти доводы в скобках;
 * скобки считаются с учётом вложенных и строк в кавычках. `@@` и `@{{` —
 * экранированный знак. Всё остальное — HTML, который разбирает
 * HTML-плагин.
 *
 * Состояние между токенами — одно из [State]: лексер обязан уметь
 * начать с середины файла, платформа перечитывает только изменённый
 * кусок.
 */
class TntTemplateLexer : LexerBase() {

    private enum class State { TEXT, OUTPUT, OUTPUT_END, RAW, RAW_END, DIRECTIVE_TAIL, ARGUMENT, ARGUMENT_END }

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null
    private var state = State.TEXT

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        state = State.entries[initialState]
        advance()
    }

    override fun getState(): Int = state.ordinal

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufferEnd) {
            tokenType = null
            return
        }
        when (state) {
            State.TEXT -> text()
            State.OUTPUT -> lua("}}", State.OUTPUT_END)
            State.OUTPUT_END -> closing("}}", TntTemplateTokens.OUTPUT_END)
            State.RAW -> lua("!!}", State.RAW_END)
            State.RAW_END -> closing("!!}", TntTemplateTokens.RAW_END)
            State.DIRECTIVE_TAIL -> directiveTail()
            State.ARGUMENT -> argument()
            State.ARGUMENT_END -> closing(")", TntTemplateTokens.RPAREN)
        }
    }

    /** Токен от [tokenStart] до [end] с типом и следующим состоянием. */
    private fun token(end: Int, type: IElementType, next: State) {
        tokenEnd = end
        tokenType = type
        state = next
    }

    private fun startsWith(at: Int, text: String): Boolean =
        at + text.length <= bufferEnd && (0 until text.length).all { buffer[at + it] == text[it] }

    /** Место `text` от `from`; пусто — нет. */
    private fun find(text: String, from: Int): Int {
        var at = from
        while (at + text.length <= bufferEnd) {
            if (startsWith(at, text)) return at
            at++
        }
        return -1
    }

    private fun text() {
        var at = tokenStart
        while (at < bufferEnd) {
            val char = buffer[at]
            if (char == '@' || (char == '{' && (startsWith(at, "{{") || startsWith(at, "{!!")))) {
                if (at > tokenStart) {
                    token(at, TntTemplateTokens.HTML, State.TEXT)
                    return
                }
                insertion(at)
                return
            }
            at++
        }
        token(bufferEnd, TntTemplateTokens.HTML, State.TEXT)
    }

    /** Вкрапление, начинающееся на `at` (там `@` либо `{`). */
    private fun insertion(at: Int) {
        if (buffer[at] == '@') {
            when {
                startsWith(at, "@@") -> token(at + 2, TntTemplateTokens.ESCAPE, State.TEXT)
                startsWith(at, "@{{") -> token(at + 3, TntTemplateTokens.ESCAPE, State.TEXT)
                else -> directive(at)
            }
            return
        }
        when {
            startsWith(at, "{{--") -> {
                val close = find("--}}", at + 4)
                token(if (close < 0) bufferEnd else close + 4, TntTemplateTokens.COMMENT, State.TEXT)
            }
            startsWith(at, "{{") -> token(at + 2, TntTemplateTokens.OUTPUT_START, State.OUTPUT)
            else -> token(at + 3, TntTemplateTokens.RAW_START, State.RAW)
        }
    }

    private fun directive(at: Int) {
        var end = at + 1
        if (end < bufferEnd && (buffer[end].isLetter() || buffer[end] == '_')) {
            while (end < bufferEnd && (buffer[end].isLetterOrDigit() || buffer[end] == '_')) end++
            token(end, TntTemplateTokens.DIRECTIVE, State.DIRECTIVE_TAIL)
        } else {
            // Одинокий `@` — просто знак в тексте.
            token(end, TntTemplateTokens.HTML, State.TEXT)
        }
    }

    /** После имени директивы: пробелы и скобка доводов либо обычный текст. */
    private fun directiveTail() {
        var at = tokenStart
        while (at < bufferEnd && (buffer[at] == ' ' || buffer[at] == '\t')) at++
        when {
            at < bufferEnd && buffer[at] == '(' && at > tokenStart -> token(at, TokenType.WHITE_SPACE, State.DIRECTIVE_TAIL)
            at < bufferEnd && buffer[at] == '(' -> token(at + 1, TntTemplateTokens.LPAREN, State.ARGUMENT)
            else -> {
                state = State.TEXT
                text()
            }
        }
    }

    /** Доводы до парной закрывающей скобки: вложенные скобки и кавычки учитываются. */
    private fun argument() {
        var depth = 1
        var quote: Char? = null
        var at = tokenStart
        while (at < bufferEnd) {
            val char = buffer[at]
            if (quote != null) {
                if (char == '\\') at++ else if (char == quote) quote = null
            } else when (char) {
                '"', '\'' -> quote = char
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        if (at > tokenStart) token(at, TntTemplateTokens.LUA, State.ARGUMENT_END) else token(at + 1, TntTemplateTokens.RPAREN, State.TEXT)
                        return
                    }
                }
            }
            at++
        }
        token(bufferEnd, TntTemplateTokens.LUA, State.TEXT)
    }

    /** Выражение до закрывающих скобок вывода. */
    private fun lua(closing: String, next: State) {
        val close = find(closing, tokenStart)
        when {
            close < 0 -> token(bufferEnd, TntTemplateTokens.LUA, State.TEXT)
            close == tokenStart -> {
                state = next
                closing(closing, if (next == State.OUTPUT_END) TntTemplateTokens.OUTPUT_END else TntTemplateTokens.RAW_END)
            }
            else -> token(close, TntTemplateTokens.LUA, next)
        }
    }

    private fun closing(text: String, type: IElementType) {
        token(tokenStart + text.length, type, State.TEXT)
    }
}
