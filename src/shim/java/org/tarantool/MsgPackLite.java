package org.tarantool;

import com.khovanskiy.tarantool.jdbcshim.MsgPackExtension;
import com.khovanskiy.tarantool.jdbcshim.TarantoolInterval;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * forked from https://bitbucket.org/sirbrialliance/msgpack-java-lite
 *
 * Форк MsgPackLite из org.tarantool:connector:1.9.4 с поддержкой
 * MsgPack-расширений Tarantool: decimal (1), uuid (2), datetime (4),
 * interval (6); прочие расширения возвращаются сырыми байтами.
 * Оригинал ext-семейство (0xc7–0xc9, 0xd4–0xd8) не разбирает вовсе
 * и первым же таким значением валит поток чтения соединения
 * («Input contains invalid type value -40»). При сборке shim-jar
 * этот класс замещает оригинальный — тот исключён из шейдинга.
 */
public class MsgPackLite {

    public static final MsgPackLite INSTANCE = new MsgPackLite();

    protected static final int MAX_4BIT = 0xf;
    protected static final int MAX_5BIT = 0x1f;
    protected static final int MAX_7BIT = 0x7f;
    protected static final int MAX_8BIT = 0xff;
    protected static final int MAX_15BIT = 0x7fff;
    protected static final int MAX_16BIT = 0xffff;
    protected static final int MAX_31BIT = 0x7fffffff;
    protected static final long MAX_32BIT = 0xffffffffL;

    protected static final BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    protected static final BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    protected static final BigInteger BI_MAX_64BIT = BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE);

    //these values are from http://wiki.msgpack.org/display/MSGPACK/Format+specification
    protected static final byte MP_NULL = (byte) 0xc0;
    protected static final byte MP_FALSE = (byte) 0xc2;
    protected static final byte MP_TRUE = (byte) 0xc3;
    protected static final byte MP_BIN8 = (byte) 0xc4;
    protected static final byte MP_BIN16 = (byte) 0xc5;
    protected static final byte MP_BIN32 = (byte) 0xc6;

    protected static final byte MP_EXT8 = (byte) 0xc7;
    protected static final byte MP_EXT16 = (byte) 0xc8;
    protected static final byte MP_EXT32 = (byte) 0xc9;

    protected static final byte MP_FLOAT = (byte) 0xca;
    protected static final byte MP_DOUBLE = (byte) 0xcb;

    protected static final byte MP_FIXNUM = (byte) 0x00;//last 7 bits is value
    protected static final byte MP_UINT8 = (byte) 0xcc;
    protected static final byte MP_UINT16 = (byte) 0xcd;
    protected static final byte MP_UINT32 = (byte) 0xce;
    protected static final byte MP_UINT64 = (byte) 0xcf;

    protected static final byte MP_NEGATIVE_FIXNUM = (byte) 0xe0;//last 5 bits is value
    protected static final int MP_NEGATIVE_FIXNUM_INT = 0xe0;//  /me wishes for signed numbers.
    protected static final byte MP_INT8 = (byte) 0xd0;
    protected static final byte MP_INT16 = (byte) 0xd1;
    protected static final byte MP_INT32 = (byte) 0xd2;
    protected static final byte MP_INT64 = (byte) 0xd3;

    protected static final byte MP_FIXEXT1 = (byte) 0xd4;
    protected static final byte MP_FIXEXT2 = (byte) 0xd5;
    protected static final byte MP_FIXEXT4 = (byte) 0xd6;
    protected static final byte MP_FIXEXT8 = (byte) 0xd7;
    protected static final byte MP_FIXEXT16 = (byte) 0xd8;

    protected static final byte MP_FIXARRAY = (byte) 0x90;//last 4 bits is size
    protected static final int MP_FIXARRAY_INT = 0x90;
    protected static final byte MP_ARRAY16 = (byte) 0xdc;
    protected static final byte MP_ARRAY32 = (byte) 0xdd;

    protected static final byte MP_FIXMAP = (byte) 0x80;//last 4 bits is size
    protected static final int MP_FIXMAP_INT = 0x80;
    protected static final byte MP_MAP16 = (byte) 0xde;
    protected static final byte MP_MAP32 = (byte) 0xdf;

    protected static final byte MP_FIXSTR = (byte) 0xa0;//last 5 bits is size
    protected static final int MP_FIXSTR_INT = 0xa0;
    protected static final byte MP_STR8 = (byte) 0xd9;
    protected static final byte MP_STR16 = (byte) 0xda;
    protected static final byte MP_STR32 = (byte) 0xdb;

    // Типы MsgPack-расширений Tarantool:
    // https://www.tarantool.io/en/doc/latest/dev_guide/internals/msgpack_extensions/
    protected static final int EXT_DECIMAL = 1;
    protected static final int EXT_UUID = 2;
    protected static final int EXT_DATETIME = 4;
    protected static final int EXT_INTERVAL = 6;

    // Знаковые полубайты packed BCD: 0x0b и 0x0d — минус, остальные — плюс
    private static final int BCD_SIGN_PLUS = 0x0c;
    private static final int BCD_SIGN_MINUS = 0x0d;

    public void pack(Object item, OutputStream os) throws IOException {
        DataOutputStream out = new DataOutputStream(os);
        if (item instanceof Callable) {
            try {
                item = ((Callable) item).call();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (item == null) {
            out.write(MP_NULL);
        } else if (item instanceof Boolean) {
            out.write(((Boolean) item).booleanValue() ? MP_TRUE : MP_FALSE);
        } else if (item instanceof BigDecimal) {
            // раньше ветки Number: она обрезала бы значение до long
            packDecimal((BigDecimal) item, out);
        } else if (item instanceof UUID) {
            packUuid((UUID) item, out);
        } else if (item instanceof OffsetDateTime) {
            OffsetDateTime dt = (OffsetDateTime) item;
            packDatetime(dt.toEpochSecond(), dt.getNano(), dt.getOffset().getTotalSeconds(), out);
        } else if (item instanceof ZonedDateTime) {
            OffsetDateTime dt = ((ZonedDateTime) item).toOffsetDateTime();
            packDatetime(dt.toEpochSecond(), dt.getNano(), dt.getOffset().getTotalSeconds(), out);
        } else if (item instanceof Instant) {
            Instant instant = (Instant) item;
            packDatetime(instant.getEpochSecond(), instant.getNano(), 0, out);
        } else if (item instanceof Timestamp) {
            Instant instant = ((Timestamp) item).toInstant();
            packDatetime(instant.getEpochSecond(), instant.getNano(), 0, out);
        } else if (item instanceof java.util.Date) {
            // java.sql.Date и java.sql.Time бросают из toInstant() — через миллисекунды
            Instant instant = Instant.ofEpochMilli(((java.util.Date) item).getTime());
            packDatetime(instant.getEpochSecond(), instant.getNano(), 0, out);
        } else if (item instanceof TarantoolInterval) {
            packInterval((TarantoolInterval) item, out);
        } else if (item instanceof MsgPackExtension) {
            MsgPackExtension ext = (MsgPackExtension) item;
            byte[] payload = ext.getPayload();
            writeExtHeader(payload.length, ext.getType(), out);
            out.write(payload);
        } else if (item instanceof Number || item instanceof Code) {
            if (item instanceof Float) {
                out.write(MP_FLOAT);
                out.writeFloat((Float) item);
            } else if (item instanceof Double) {
                out.write(MP_DOUBLE);
                out.writeDouble((Double) item);
            } else {
                if (item instanceof BigInteger) {
                    BigInteger value = (BigInteger) item;
                    boolean isPositive = value.signum() >= 0;
                    if (isPositive && value.compareTo(BI_MAX_64BIT) > 0 ||
                        value.compareTo(BI_MIN_LONG) < 0) {
                        throw new IllegalArgumentException(
                            "Cannot encode BigInteger as MsgPack: out of -2^63..2^64-1 range");
                    }
                    if (isPositive && value.compareTo(BI_MAX_LONG) > 0) {
                        byte[] data = value.toByteArray();
                        // data can contain leading zero bytes
                        for (int i = 0; i < data.length - 8; ++i) {
                            assert data[i] == 0;
                        }
                        out.write(MP_UINT64);
                        out.write(data, data.length - 8, 8);
                        return;
                    }
                }
                long value = item instanceof Code ? ((Code) item).getId() : ((Number) item).longValue();
                if (value >= 0) {
                    if (value <= MAX_7BIT) {
                        out.write((int) value | MP_FIXNUM);
                    } else if (value <= MAX_8BIT) {
                        out.write(MP_UINT8);
                        out.write((int) value);
                    } else if (value <= MAX_16BIT) {
                        out.write(MP_UINT16);
                        out.writeShort((int) value);
                    } else if (value <= MAX_32BIT) {
                        out.write(MP_UINT32);
                        out.writeInt((int) value);
                    } else {
                        out.write(MP_UINT64);
                        out.writeLong(value);
                    }
                } else {
                    if (value >= -(MAX_5BIT + 1)) {
                        out.write((int) (value & 0xff));
                    } else if (value >= -(MAX_7BIT + 1)) {
                        out.write(MP_INT8);
                        out.write((int) value);
                    } else if (value >= -(MAX_15BIT + 1)) {
                        out.write(MP_INT16);
                        out.writeShort((int) value);
                    } else if (value >= -(MAX_31BIT + 1)) {
                        out.write(MP_INT32);
                        out.writeInt((int) value);
                    } else {
                        out.write(MP_INT64);
                        out.writeLong(value);
                    }
                }
            }
        } else if (item instanceof String) {
            byte[] data = ((String) item).getBytes("UTF-8");
            if (data.length <= MAX_5BIT) {
                out.write(data.length | MP_FIXSTR);
            } else if (data.length <= MAX_8BIT) {
                out.write(MP_STR8);
                out.writeByte(data.length);
            } else if (data.length <= MAX_16BIT) {
                out.write(MP_STR16);
                out.writeShort(data.length);
            } else {
                out.write(MP_STR32);
                out.writeInt(data.length);
            }
            out.write(data);
        } else if (item instanceof byte[] || item instanceof ByteBuffer) {
            byte[] data;
            if (item instanceof byte[]) {
                data = (byte[]) item;
            } else {
                ByteBuffer bb = ((ByteBuffer) item);
                if (bb.hasArray()) {
                    data = bb.array();
                } else {
                    data = new byte[bb.capacity()];
                    bb.position();
                    bb.limit(bb.capacity());
                    bb.get(data);
                }
            }
            if (data.length <= MAX_8BIT) {
                out.write(MP_BIN8);
                out.writeByte(data.length);
            } else if (data.length <= MAX_16BIT) {
                out.write(MP_BIN16);
                out.writeShort(data.length);
            } else {
                out.write(MP_BIN32);
                out.writeInt(data.length);
            }
            out.write(data);
        } else if (item instanceof List || item.getClass().isArray()) {
            int length = item instanceof List ? ((List) item).size() : Array.getLength(item);
            if (length <= MAX_4BIT) {
                out.write(length | MP_FIXARRAY);
            } else if (length <= MAX_16BIT) {
                out.write(MP_ARRAY16);
                out.writeShort(length);
            } else {
                out.write(MP_ARRAY32);
                out.writeInt(length);
            }
            if (item instanceof List) {
                List list = ((List) item);
                for (Object element : list) {
                    pack(element, out);
                }
            } else {
                for (int i = 0; i < length; i++) {
                    pack(Array.get(item, i), out);
                }
            }
        } else if (item instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) item;
            if (map.size() <= MAX_4BIT) {
                out.write(map.size() | MP_FIXMAP);
            } else if (map.size() <= MAX_16BIT) {
                out.write(MP_MAP16);
                out.writeShort(map.size());
            } else {
                out.write(MP_MAP32);
                out.writeInt(map.size());
            }
            for (Map.Entry<Object, Object> kvp : map.entrySet()) {
                pack(kvp.getKey(), out);
                pack(kvp.getValue(), out);
            }
        } else {
            throw new IllegalArgumentException("Cannot msgpack object of type " + item.getClass().getCanonicalName());
        }
    }

    public Object unpack(InputStream is) throws IOException {
        DataInputStream in = new DataInputStream(is);
        int value = in.read();
        if (value < 0) {
            throw new IllegalArgumentException("No more input available when expecting a value");
        }
        switch ((byte) value) {
        case MP_NULL:
            return null;
        case MP_FALSE:
            return false;
        case MP_TRUE:
            return true;
        case MP_FLOAT:
            return in.readFloat();
        case MP_DOUBLE:
            return in.readDouble();
        case MP_UINT8:
            return in.read(); // read single byte, return as int
        case MP_UINT16:
            return in.readShort() & MAX_16BIT; // read short, trick Java into treating it as unsigned, return int
        case MP_UINT32:
            return in.readInt() & MAX_32BIT; // read int, trick Java into treating it as unsigned, return long
        case MP_UINT64: {
            long v = in.readLong();
            if (v >= 0) {
                return v;
            } else {
                // this is a little bit more tricky, since we don't have unsigned longs
                byte[] bytes = new byte[] {
                    (byte) ((v >> 56) & 0xff),
                    (byte) ((v >> 48) & 0xff),
                    (byte) ((v >> 40) & 0xff),
                    (byte) ((v >> 32) & 0xff),
                    (byte) ((v >> 24) & 0xff),
                    (byte) ((v >> 16) & 0xff),
                    (byte) ((v >> 8) & 0xff),
                    (byte) (v & 0xff),
                };
                return new BigInteger(1, bytes);
            }
        }
        case MP_INT8:
            return (byte) in.read();
        case MP_INT16:
            return in.readShort();
        case MP_INT32:
            return in.readInt();
        case MP_INT64:
            return in.readLong();
        case MP_FIXEXT1:
            return unpackExt(1, in);
        case MP_FIXEXT2:
            return unpackExt(2, in);
        case MP_FIXEXT4:
            return unpackExt(4, in);
        case MP_FIXEXT8:
            return unpackExt(8, in);
        case MP_FIXEXT16:
            return unpackExt(16, in);
        case MP_EXT8:
            return unpackExt(in.readByte() & MAX_8BIT, in);
        case MP_EXT16:
            return unpackExt(in.readShort() & MAX_16BIT, in);
        case MP_EXT32:
            return unpackExt(in.readInt(), in);
        case MP_ARRAY16:
            return unpackList(in.readShort() & MAX_16BIT, in);
        case MP_ARRAY32:
            return unpackList(in.readInt(), in);
        case MP_MAP16:
            return unpackMap(in.readShort() & MAX_16BIT, in);
        case MP_MAP32:
            return unpackMap(in.readInt(), in);
        case MP_STR8:
            return unpackStr(in.readByte() & MAX_8BIT, in);
        case MP_STR16:
            return unpackStr(in.readShort() & MAX_16BIT, in);
        case MP_STR32:
            return unpackStr(in.readInt(), in);
        case MP_BIN8:
            return unpackBin(in.readByte() & MAX_8BIT, in);
        case MP_BIN16:
            return unpackBin(in.readShort() & MAX_16BIT, in);
        case MP_BIN32:
            return unpackBin(in.readInt(), in);
        default:
            break;
        }

        if (value >= MP_NEGATIVE_FIXNUM_INT && value <= MP_NEGATIVE_FIXNUM_INT + MAX_5BIT) {
            return (byte) value;
        } else if (value >= MP_FIXARRAY_INT && value <= MP_FIXARRAY_INT + MAX_4BIT) {
            return unpackList(value - MP_FIXARRAY_INT, in);
        } else if (value >= MP_FIXMAP_INT && value <= MP_FIXMAP_INT + MAX_4BIT) {
            return unpackMap(value - MP_FIXMAP_INT, in);
        } else if (value >= MP_FIXSTR_INT && value <= MP_FIXSTR_INT + MAX_5BIT) {
            return unpackStr(value - MP_FIXSTR_INT, in);
        } else if (value <= MAX_7BIT) {
            // MP_FIXNUM - the value is value as an int
            return value;
        } else {
            throw new IllegalArgumentException("Input contains invalid type value " + (byte) value);
        }
    }

    protected List unpackList(int size, DataInputStream in) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Array to unpack too large for Java (more than 2^31 elements)!");
        }
        List ret = new ArrayList(size);
        for (int i = 0; i < size; ++i) {
            ret.add(unpack(in));
        }
        return ret;
    }

    protected Map unpackMap(int size, DataInputStream in) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Map to unpack too large for Java (more than 2^31 elements)!");
        }
        Map ret = new HashMap(size);
        for (int i = 0; i < size; ++i) {
            Object key = unpack(in);
            Object value = unpack(in);
            ret.put(key, value);
        }
        return ret;
    }

    protected Object unpackStr(int size, DataInputStream in) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("byte[] to unpack too large for Java (more than 2^31 elements)!");
        }

        byte[] data = new byte[size];
        in.readFully(data);
        return new String(data, "UTF-8");
    }

    protected Object unpackBin(int size, DataInputStream in) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("byte[] to unpack too large for Java (more than 2^31 elements)!");
        }

        byte[] data = new byte[size];
        in.readFully(data);
        return data;
    }

    protected Object unpackExt(int size, DataInputStream in) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("ext to unpack too large for Java (more than 2^31 bytes)!");
        }
        int type = in.readByte();
        byte[] payload = new byte[size];
        in.readFully(payload);
        try {
            switch (type) {
            case EXT_DECIMAL:
                return decodeDecimal(payload);
            case EXT_UUID:
                return decodeUuid(payload);
            case EXT_DATETIME:
                return decodeDatetime(payload);
            case EXT_INTERVAL:
                return decodeInterval(payload);
            default:
                return new MsgPackExtension(type, payload);
            }
        } catch (Exception e) {
            // Неразборчивый payload не должен убивать поток чтения соединения:
            // страдает одна ячейка, а не всё подключение — отдаём сырые байты.
            return new MsgPackExtension(type, payload);
        }
    }

    /**
     * MP_DECIMAL: msgpack-число «scale», затем packed BCD — полубайты-цифры
     * старшими вперёд, последний полубайт знаковый.
     */
    protected BigDecimal decodeDecimal(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        int scale = readIntExact(unpack(in), "decimal scale");
        byte[] bcd = new byte[in.available()];
        in.readFully(bcd);
        if (bcd.length == 0) {
            throw new IOException("decimal payload without digits");
        }
        int nibbleCount = bcd.length * 2;
        StringBuilder digits = new StringBuilder(nibbleCount - 1);
        for (int i = 0; i < nibbleCount - 1; i++) {
            int nibble = nibbleAt(bcd, i);
            if (nibble > 9) {
                throw new IOException("invalid BCD digit nibble " + nibble);
            }
            digits.append((char) ('0' + nibble));
        }
        int sign = nibbleAt(bcd, nibbleCount - 1);
        boolean negative = sign == 0x0b || sign == BCD_SIGN_MINUS;
        if (!negative && sign != 0x0a && sign != BCD_SIGN_PLUS && sign != 0x0e && sign != 0x0f) {
            throw new IOException("invalid BCD sign nibble " + sign);
        }
        BigDecimal result = new BigDecimal(new BigInteger(digits.toString()), scale);
        return negative ? result.negate() : result;
    }

    /** MP_UUID: 16 байт в сетевом (big-endian) порядке. */
    protected UUID decodeUuid(byte[] payload) throws IOException {
        if (payload.length != 16) {
            throw new IOException("uuid payload must be 16 bytes, got " + payload.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * MP_DATETIME: little-endian; 8 байт — секунды эпохи, либо 16 байт —
     * секунды + nsec (int32) + смещение зоны в минутах (int16) + tzindex (int16).
     *
     * Возвращается java.sql.Timestamp, а не OffsetDateTime: колонки
     * datetime объявлены как Types.TIMESTAMP, а редактор данных IDE
     * гоняет значения через мост remote-JDBC-процесса, где чуждый JDBC
     * тип деградирует в строку — и UPDATE ... WHERE created_at = ?
     * падал на сервере («can not convert string to datetime»).
     * Timestamp мост переживает, и упаковка Timestamp -> ext уже есть.
     * Смещение зоны (и tzindex) при этом теряется только в отображении:
     * момент времени сохраняется, а равенство datetime Tarantool
     * сравнивает по физическому времени, без учёта tzoffset.
     */
    protected Timestamp decodeDatetime(byte[] payload) throws IOException {
        if (payload.length != 8 && payload.length != 16) {
            throw new IOException("datetime payload must be 8 or 16 bytes, got " + payload.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        long seconds = buffer.getLong();
        int nanos = 0;
        if (payload.length == 16) {
            nanos = buffer.getInt();
        }
        if (nanos < 0 || nanos >= 1_000_000_000) {
            // Instant.ofEpochSecond молча нормализовал бы такой nsec,
            // сдвинув момент времени — честнее сырые байты
            throw new IOException("datetime nsec out of range: " + nanos);
        }
        return Timestamp.from(Instant.ofEpochSecond(seconds, nanos));
    }

    /**
     * MP_INTERVAL: msgpack-число полей, затем пары (id поля, значение);
     * id: 0 year, 1 month, 2 week, 3 day, 4 hour, 5 min, 6 sec, 7 nsec, 8 adjust.
     */
    protected TarantoolInterval decodeInterval(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        int count = readIntExact(unpack(in), "interval field count");
        long year = 0;
        long month = 0;
        long week = 0;
        long day = 0;
        long hour = 0;
        long min = 0;
        long sec = 0;
        long nsec = 0;
        int adjust = 0;
        for (int i = 0; i < count; i++) {
            int field = readIntExact(unpack(in), "interval field id");
            long value = readLongExact(unpack(in), "interval field value");
            switch (field) {
            case 0:
                year = value;
                break;
            case 1:
                month = value;
                break;
            case 2:
                week = value;
                break;
            case 3:
                day = value;
                break;
            case 4:
                hour = value;
                break;
            case 5:
                min = value;
                break;
            case 6:
                sec = value;
                break;
            case 7:
                nsec = value;
                break;
            case 8:
                adjust = (int) value;
                break;
            default:
                // поле новее шима — сырые байты честнее молчаливой потери данных
                throw new IOException("unknown interval field " + field);
            }
        }
        if (in.available() > 0) {
            throw new IOException("trailing bytes in interval payload");
        }
        return new TarantoolInterval(year, month, week, day, hour, min, sec, nsec, adjust);
    }

    protected void packDecimal(BigDecimal value, DataOutputStream out) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream payloadOut = new DataOutputStream(payload);
        pack(value.scale(), payloadOut);
        String coefficient = value.unscaledValue().abs().toString();
        if (coefficient.length() % 2 == 0) {
            // полубайтов вместе со знаковым должно быть чётное число — ведущий ноль
            coefficient = "0" + coefficient;
        }
        byte[] bcd = new byte[(coefficient.length() + 1) / 2];
        for (int i = 0; i < coefficient.length(); i++) {
            int digit = coefficient.charAt(i) - '0';
            bcd[i / 2] |= i % 2 == 0 ? digit << 4 : digit;
        }
        bcd[bcd.length - 1] |= value.signum() < 0 ? BCD_SIGN_MINUS : BCD_SIGN_PLUS;
        payloadOut.write(bcd);
        writeExtHeader(payload.size(), EXT_DECIMAL, out);
        payload.writeTo(out);
    }

    protected void packUuid(UUID value, DataOutputStream out) throws IOException {
        out.write(MP_FIXEXT16);
        out.write(EXT_UUID);
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    protected void packDatetime(long epochSeconds, int nanos, int offsetSeconds, DataOutputStream out)
        throws IOException {
        // Формат хранит смещение в минутах — секундная часть исторических
        // смещений (LMT и т.п.) отбрасывается; сам момент времени точен,
        // страдает только отображаемая зона.
        int tzMinutes = offsetSeconds / 60;
        if (tzMinutes < -720 || tzMinutes > 840) {
            // Диапазон Tarantool (datetime.h: TZOFFSET_MIN/MAX); сервер
            // отверг бы значение при декодировании — падаем раньше и понятнее
            throw new IllegalArgumentException(
                "Time zone offset " + tzMinutes + " min is out of Tarantool range [-720, 840]");
        }
        if (nanos == 0 && tzMinutes == 0) {
            out.write(MP_FIXEXT8);
            out.write(EXT_DATETIME);
            ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(epochSeconds);
            out.write(buffer.array());
        } else {
            out.write(MP_FIXEXT16);
            out.write(EXT_DATETIME);
            ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(epochSeconds);
            buffer.putInt(nanos);
            buffer.putShort((short) tzMinutes);
            buffer.putShort((short) 0); // tzindex неизвестен, 0 — «зона не задана»
            out.write(buffer.array());
        }
    }

    protected void packInterval(TarantoolInterval value, DataOutputStream out) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream payloadOut = new DataOutputStream(payload);
        long[] fields = {
            value.getYear(), value.getMonth(), value.getWeek(), value.getDay(),
            value.getHour(), value.getMin(), value.getSec(), value.getNsec(), value.getAdjust(),
        };
        int count = 0;
        for (long field : fields) {
            if (field != 0) {
                count++;
            }
        }
        pack(count, payloadOut);
        for (int id = 0; id < fields.length; id++) {
            if (fields[id] != 0) {
                pack(id, payloadOut);
                pack(fields[id], payloadOut);
            }
        }
        writeExtHeader(payload.size(), EXT_INTERVAL, out);
        payload.writeTo(out);
    }

    protected void writeExtHeader(int length, int type, DataOutputStream out) throws IOException {
        switch (length) {
        case 1:
            out.write(MP_FIXEXT1);
            break;
        case 2:
            out.write(MP_FIXEXT2);
            break;
        case 4:
            out.write(MP_FIXEXT4);
            break;
        case 8:
            out.write(MP_FIXEXT8);
            break;
        case 16:
            out.write(MP_FIXEXT16);
            break;
        default:
            if (length <= MAX_8BIT) {
                out.write(MP_EXT8);
                out.writeByte(length);
            } else if (length <= MAX_16BIT) {
                out.write(MP_EXT16);
                out.writeShort(length);
            } else {
                out.write(MP_EXT32);
                out.writeInt(length);
            }
        }
        out.write(type);
    }

    private static int nibbleAt(byte[] data, int index) {
        int b = data[index / 2] & 0xff;
        return index % 2 == 0 ? b >>> 4 : b & 0x0f;
    }

    /**
     * Число из msgpack-значения строго в диапазоне int: intValue() молча
     * заворачивал бы uint32/uint64 с провода, искажая значение вместо
     * raw-фолбэка в unpackExt.
     */
    private static int readIntExact(Object value, String what) throws IOException {
        long result = readLongExact(value, what);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IOException(what + " out of int range: " + result);
        }
        return (int) result;
    }

    /** Число строго в диапазоне long: longValue() молча обрезал бы BigInteger. */
    private static long readLongExact(Object value, String what) throws IOException {
        if (!(value instanceof Number) || value instanceof BigInteger
            || value instanceof Float || value instanceof Double) {
            throw new IOException(what + " has unexpected type: "
                + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return ((Number) value).longValue();
    }
}
