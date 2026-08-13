package org.tarantool.jdbc;

import org.tarantool.MsgPackLite;

/**
 * Форк SQLMsgPackLite из org.tarantool:connector:1.9.4; при сборке
 * shim-jar замещает оригинал (тот исключён из шейдинга).
 *
 * Оригинал перехватывал Date/Time/Timestamp (кодировал long-миллисекундами)
 * и BigDecimal (кодировал строкой) — до появления в Tarantool родных типов
 * это был единственный вариант. Теперь базовый форк MsgPackLite кодирует
 * эти значения родными MsgPack-расширениями (datetime, decimal), поэтому
 * перехваты удалены.
 */
public class SQLMsgPackLite extends MsgPackLite {

    public static final SQLMsgPackLite INSTANCE = new SQLMsgPackLite();
}
