package com.khovanskiy.tarantool

import com.khovanskiy.tarantool.jdbcshim.MsgPackExtension
import com.khovanskiy.tarantool.jdbcshim.TarantoolInterval
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tarantool.MsgPackLite
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Форк MsgPackLite: разбор MsgPack-расширений Tarantool (decimal, uuid,
 * datetime, interval), которые оригинальный класс из коннектора 1.9.4
 * не понимает и валит соединение («Input contains invalid type value -40»).
 *
 * Байтовые векторы собраны по спецификации:
 * https://www.tarantool.io/en/doc/latest/dev_guide/internals/msgpack_extensions/
 */
class MsgPackLiteExtTest {

    private fun unpack(data: ByteArray): Any? = MsgPackLite.INSTANCE.unpack(ByteArrayInputStream(data))

    private fun pack(value: Any?): ByteArray {
        val out = ByteArrayOutputStream()
        MsgPackLite.INSTANCE.pack(value, out)
        return out.toByteArray()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // --- uuid: fixext16, тип 2, 16 байт big-endian ---

    @Test
    fun `uuid decodes from fixext16`() {
        val payload = bytes(
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff,
        )
        val result = unpack(bytes(0xd8, 0x02) + payload)
        assertEquals(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), result)
    }

    @Test
    fun `uuid consumes exactly its payload`() {
        val data = bytes(0xd8, 0x02) + ByteArray(16) + bytes(0x2a)
        val input = ByteArrayInputStream(data)
        MsgPackLite.INSTANCE.unpack(input)
        // следом лежит fixnum 42 — расширение не должно ни съесть его, ни недочитать себя
        assertEquals(42, MsgPackLite.INSTANCE.unpack(input))
        assertEquals(0, input.available())
    }

    // --- datetime: fixext8/fixext16, тип 4, little-endian ---

    @Test
    fun `datetime decodes from fixext8 as utc`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(instant.epochSecond).array()
        val result = unpack(bytes(0xd7, 0x04) + payload)
        assertEquals(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC), result)
    }

    @Test
    fun `datetime decodes from fixext16 with nanos and zone offset`() {
        val seconds = Instant.parse("2026-01-01T00:00:00Z").epochSecond
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(seconds)
            .putInt(123_456_789)
            .putShort(180) // +03:00 в минутах
            .putShort(0)
            .array()
        val result = unpack(bytes(0xd8, 0x04) + payload)
        val expected = OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(seconds, 123_456_789),
            ZoneOffset.ofHours(3),
        )
        assertEquals(expected, result)
    }

    @Test
    fun `datetime decodes negative epoch seconds`() {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(-1).array()
        val result = unpack(bytes(0xd7, 0x04) + payload)
        assertEquals(OffsetDateTime.parse("1969-12-31T23:59:59Z"), result)
    }

    // --- decimal: ext8, тип 1, msgpack-scale + packed BCD ---

    @Test
    fun `decimal decodes doc example 1_1`() {
        // пример из документации: 1.1 → scale=1, BCD 01 1c
        assertEquals(BigDecimal("1.1"), unpack(bytes(0xc7, 0x03, 0x01, 0x01, 0x01, 0x1c)))
    }

    @Test
    fun `decimal decodes negative value`() {
        assertEquals(BigDecimal("-1.1"), unpack(bytes(0xc7, 0x03, 0x01, 0x01, 0x01, 0x1d)))
    }

    @Test
    fun `decimal supports all sign nibbles`() {
        for (plus in intArrayOf(0x0a, 0x0c, 0x0e, 0x0f)) {
            assertEquals(BigDecimal("7"), unpack(bytes(0xc7, 0x02, 0x01, 0x00, 0x70 or plus)))
        }
        for (minus in intArrayOf(0x0b, 0x0d)) {
            assertEquals(BigDecimal("-7"), unpack(bytes(0xc7, 0x02, 0x01, 0x00, 0x70 or minus)))
        }
    }

    @Test
    fun `decimal decodes negative scale`() {
        // scale = -2 (negative fixnum 0xfe), коэффициент 1 → 1E+2
        assertEquals(BigDecimal("1E+2"), unpack(bytes(0xc7, 0x02, 0x01, 0xfe, 0x1c)))
    }

    @Test
    fun `malformed decimal falls back to raw extension instead of dying`() {
        // знаковый полубайт 0x05 невалиден — значение отдаётся сырыми байтами
        val result = unpack(bytes(0xc7, 0x02, 0x01, 0x01, 0x15))
        assertEquals(MsgPackExtension(1, bytes(0x01, 0x15)), result)
    }

    // --- interval: ext8, тип 6, число полей + пары (id, значение) ---

    @Test
    fun `interval decodes fields`() {
        // 2 поля: year=1, month=2
        val result = unpack(bytes(0xc7, 0x05, 0x06, 0x02, 0x00, 0x01, 0x01, 0x02))
        assertEquals(TarantoolInterval(1, 2, 0, 0, 0, 0, 0, 0, 0), result)
    }

    @Test
    fun `interval decodes negative values`() {
        // 1 поле: sec = -5 (negative fixnum 0xfb)
        val result = unpack(bytes(0xc7, 0x03, 0x06, 0x01, 0x06, 0xfb))
        assertEquals(TarantoolInterval(0, 0, 0, 0, 0, 0, -5, 0, 0), result)
    }

    // --- искажённые payload: фолбэк в сырые байты вместо тихого искажения ---

    @Test
    fun `decimal with overflowing scale falls back to raw extension`() {
        // scale = uint32 2^31 не влезает в int — intValue() исказил бы знак
        val payload = bytes(0xce, 0x80, 0x00, 0x00, 0x00, 0x1c)
        assertEquals(MsgPackExtension(1, payload), unpack(bytes(0xc7, 0x06, 0x01) + payload))
    }

    @Test
    fun `datetime with negative nsec falls back to raw extension`() {
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(0).putInt(-1).putShort(0).putShort(0).array()
        assertEquals(MsgPackExtension(4, payload), unpack(bytes(0xd8, 0x04) + payload))
    }

    @Test
    fun `interval with uint64 value falls back to raw extension`() {
        // day = 2^64-1 приходит BigInteger'ом — longValue() обрезал бы до -1
        val payload = bytes(0x01, 0x03, 0xcf, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)
        assertEquals(MsgPackExtension(6, payload), unpack(bytes(0xc7, 0x0b, 0x06) + payload))
    }

    @Test
    fun `extension type outside int8 is rejected at construction`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            MsgPackExtension(300, bytes(0x01))
        }
    }

    @Test
    fun `datetime with offset outside tarantool range is rejected at pack`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            pack(OffsetDateTime.parse("2026-08-13T10:00:00+15:00"))
        }
    }

    // --- неизвестные расширения ---

    @Test
    fun `unknown extension type is preserved as raw bytes`() {
        val result = unpack(bytes(0xd4, 0x2a, 0xff))
        assertEquals(MsgPackExtension(42, bytes(0xff)), result)
    }

    // --- регрессия: сценарий из бага ---

    @Test
    fun `extension inside nested response structure decodes`() {
        // Структура тела IPROTO-ответа: map { DATA(48) → [[id, uuid], ...] } —
        // на таком ответе оригинальный класс падал с invalid type value -40
        val uuidExt = bytes(0xd8, 0x02) + ByteArray(16) { it.toByte() }
        val body = bytes(0x81, 0x30, 0x91, 0x92, 0x01) + uuidExt
        val result = unpack(body)
        @Suppress("UNCHECKED_CAST")
        val rows = (result as Map<Any?, Any?>)[48] as List<List<Any?>>
        assertEquals(1, rows[0][0])
        assertTrue(rows[0][1] is UUID)
    }

    // --- round-trip: pack → unpack ---

    @Test
    fun `uuid round-trip`() {
        val value = UUID.fromString("6f2c8d9e-1a3b-4c5d-8e7f-901234567890")
        assertEquals(value, unpack(pack(value)))
    }

    @Test
    fun `decimal round-trip keeps scale and sign`() {
        for (text in listOf("123.456", "-0.5", "0", "1E+3", "-98765432109876543210.123")) {
            val value = BigDecimal(text)
            assertEquals(value, unpack(pack(value)), "round-trip для $text")
        }
    }

    @Test
    fun `decimal packs as extension not as long`() {
        val packed = pack(BigDecimal("1.1"))
        // пример из документации: c7 03 01 01 01 1c
        assertArrayEquals(bytes(0xc7, 0x03, 0x01, 0x01, 0x01, 0x1c), packed)
    }

    @Test
    fun `datetime round-trip utc without nanos uses short form`() {
        val value = OffsetDateTime.parse("2026-08-13T10:00:00Z")
        val packed = pack(value)
        assertEquals(0xd7.toByte(), packed[0], "секунды без зоны и наносекунд — fixext8")
        assertEquals(value, unpack(packed))
    }

    @Test
    fun `datetime round-trip with nanos and offset`() {
        val value = OffsetDateTime.parse("2026-08-13T10:00:00.123456789+03:00")
        assertEquals(value, unpack(pack(value)))
    }

    @Test
    fun `instant packs as datetime`() {
        val value = Instant.parse("2026-08-13T10:00:00.5Z")
        assertEquals(OffsetDateTime.ofInstant(value, ZoneOffset.UTC), unpack(pack(value)))
    }

    @Test
    fun `interval round-trip`() {
        val value = TarantoolInterval(1, 2, 0, 3, 0, 0, -5, 0, 1)
        assertEquals(value, unpack(pack(value)))
    }

    @Test
    fun `raw extension round-trip`() {
        val value = MsgPackExtension(42, bytes(0x01, 0x02, 0x03))
        assertEquals(value, unpack(pack(value)))
    }

    // --- путь записи JDBC-слоя: SQLMsgPackLite не должен перехватывать
    // Timestamp и BigDecimal, превращая их в long и строку ---

    @Test
    fun `jdbc layer packs timestamp as datetime extension`() {
        val out = ByteArrayOutputStream()
        val instant = Instant.parse("2026-08-13T10:00:00.123Z")
        org.tarantool.jdbc.SQLMsgPackLite.INSTANCE.pack(java.sql.Timestamp.from(instant), out)
        val packed = out.toByteArray()
        assertEquals(0xd8.toByte(), packed[0], "fixext16: наносекунды ненулевые")
        assertEquals(0x04.toByte(), packed[1], "тип расширения — datetime")
        assertEquals(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC), unpack(packed))
    }

    @Test
    fun `jdbc layer packs bigdecimal as decimal extension`() {
        val out = ByteArrayOutputStream()
        org.tarantool.jdbc.SQLMsgPackLite.INSTANCE.pack(BigDecimal("1.1"), out)
        assertArrayEquals(bytes(0xc7, 0x03, 0x01, 0x01, 0x01, 0x1c), out.toByteArray())
    }
}
