package com.khovanskiy.tarantool.jdbcshim;

import java.util.Arrays;

/**
 * Сырое MsgPack-расширение, для которого у шима нет декодера (или чей
 * payload не удалось разобрать). Хранит тип и байты как есть; при записи
 * уходит на сервер без изменений, так что значение переживает round-trip.
 */
public final class MsgPackExtension {

    private final int type;
    private final byte[] payload;

    public MsgPackExtension(int type, byte[] payload) {
        if (type < Byte.MIN_VALUE || type > Byte.MAX_VALUE) {
            // тип в msgpack — знаковый int8; без проверки запись молча
            // усекла бы его до младших 8 бит, подменив тип расширения
            throw new IllegalArgumentException("Extension type " + type + " is out of int8 range");
        }
        this.type = type;
        this.payload = payload.clone();
    }

    public int getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MsgPackExtension)) {
            return false;
        }
        MsgPackExtension that = (MsgPackExtension) other;
        return type == that.type && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return 31 * type + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        StringBuilder hex = new StringBuilder(payload.length * 2);
        for (byte b : payload) {
            hex.append(String.format("%02x", b));
        }
        return "msgpack ext " + type + ": 0x" + hex;
    }
}
