package com.khovanskiy.tarantool.jdbcshim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Locale;
import java.util.Map;

/**
 * Честные JDBC-типы в метаданных колонок.
 *
 * SQLDatabaseMetadata.getColumns() коннектора 1.9.4 отдаёт
 * DATA_TYPE = Types.OTHER (1111) для всех колонок подряд, а настоящий тип —
 * только строкой TYPE_NAME из формата спейса («unsigned», «number», ...).
 * Редактор данных IDE подбирает домен колонки по имени типа; незнакомое имя
 * означает «не число», значение фильтра квотируется строкой, и сервер
 * отвечает «Type mismatch: can not convert string('2') to unsigned».
 *
 * Обёртка приводит пару (DATA_TYPE, TYPE_NAME) к словарю, которым SQL-слой
 * Tarantool сам описывает выборки (SqlProtoUtils.getSQLMetadata): unsigned
 * и integer → BIGINT/«integer», number и double → DOUBLE/«double», и так
 * далее. Имена вне словаря (map, array, interval, ...) остаются как есть
 * с DATA_TYPE = OTHER: для них ни у IDE, ни у SQL-слоя числового домена нет.
 */
public final class ColumnTypeMapping {

    /** Номера колонок в выдаче DatabaseMetaData.getColumns по спецификации JDBC. */
    private static final int DATA_TYPE_COLUMN = 5;
    private static final int TYPE_NAME_COLUMN = 6;

    private record SqlType(int jdbcType, String typeName) {
    }

    private static final Map<String, SqlType> BY_FORMAT_TYPE = Map.ofEntries(
        Map.entry("unsigned", new SqlType(Types.BIGINT, "integer")),
        Map.entry("integer", new SqlType(Types.BIGINT, "integer")),
        Map.entry("number", new SqlType(Types.DOUBLE, "double")),
        Map.entry("double", new SqlType(Types.DOUBLE, "double")),
        Map.entry("decimal", new SqlType(Types.DECIMAL, "decimal")),
        Map.entry("string", new SqlType(Types.VARCHAR, "string")),
        Map.entry("boolean", new SqlType(Types.BOOLEAN, "boolean")),
        Map.entry("varbinary", new SqlType(Types.VARBINARY, "varbinary")),
        Map.entry("datetime", new SqlType(Types.TIMESTAMP, "datetime")),
        Map.entry("uuid", new SqlType(Types.OTHER, "uuid"))
    );

    private ColumnTypeMapping() {
    }

    /** JDBC-тип для типа из формата спейса; вне словаря — Types.OTHER. */
    public static int jdbcTypeFor(String formatType) {
        return sqlTypeFor(formatType).jdbcType;
    }

    /** Имя типа, согласованное с SQL-метаданными выборок; вне словаря — как есть. */
    public static String typeNameFor(String formatType) {
        return sqlTypeFor(formatType).typeName;
    }

    private static SqlType sqlTypeFor(String formatType) {
        if (formatType == null) {
            return new SqlType(Types.OTHER, null);
        }
        SqlType mapped = BY_FORMAT_TYPE.get(formatType.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : new SqlType(Types.OTHER, formatType);
    }

    /**
     * Оборачивает метаданные соединения: getColumns отдаёт честные типы,
     * getConnection возвращает обёрнутое соединение вместо сырого.
     */
    public static DatabaseMetaData wrap(DatabaseMetaData real, Connection connection) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
            ColumnTypeMapping.class.getClassLoader(),
            new Class<?>[]{DatabaseMetaData.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return invokeObjectMethod(proxy, method, args, "ShimDatabaseMetaData[" + real + "]");
                }
                switch (method.getName()) {
                    case "getColumns":
                        return wrapColumns((ResultSet) delegate(real, method, args));
                    case "getConnection":
                        return connection;
                    default:
                        return delegate(real, method, args);
                }
            }
        );
    }

    /**
     * Оборачивает выдачу getColumns: чтение DATA_TYPE возвращает JDBC-тип,
     * вычисленный по TYPE_NAME текущей строки, чтение TYPE_NAME — имя из
     * словаря SQL-метаданных. Остальные колонки и методы идут в делегат.
     */
    public static ResultSet wrapColumns(ResultSet columns) {
        return (ResultSet) Proxy.newProxyInstance(
            ColumnTypeMapping.class.getClassLoader(),
            new Class<?>[]{ResultSet.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return invokeObjectMethod(proxy, method, args, "ShimColumns[" + columns + "]");
                }
                if ("getStatement".equals(method.getName())) {
                    // По JDBC ResultSet из DatabaseMetaData вправе не иметь
                    // Statement; делегат вернул бы Statement сырого соединения
                    // в обход эмуляции автокоммита и этой обёртки.
                    return null;
                }
                Object column = args != null && args.length > 0 ? args[0] : null;
                if (matches(column, DATA_TYPE_COLUMN, "DATA_TYPE")) {
                    Object mapped = dataTypeValue(method, args, columns);
                    if (mapped != SKIP) {
                        return mapped;
                    }
                }
                if (matches(column, TYPE_NAME_COLUMN, "TYPE_NAME")) {
                    Object mapped = typeNameValue(method, args, columns);
                    if (mapped != SKIP) {
                        return mapped;
                    }
                }
                return delegate(columns, method, args);
            }
        );
    }

    /** Маркер «метод не про значение колонки — отдать делегату». */
    private static final Object SKIP = new Object();

    /** Колонка адресуется либо номером по JDBC, либо меткой без учёта регистра. */
    private static boolean matches(Object column, int index, String label) {
        if (column instanceof Integer i) {
            return i == index;
        }
        return column instanceof String s && s.equalsIgnoreCase(label);
    }

    private static Object dataTypeValue(Method method, Object[] args, ResultSet columns) throws Throwable {
        // Сначала фильтр по имени метода, и только потом чтение из делегата:
        // сюда попадает любой вызов с первым аргументом 5/"DATA_TYPE", включая
        // findColumn и setFetchSize, а чтение вне валидной строки бросило бы
        // «Cursor is out of range» там, где сырой драйвер работает.
        String getter = method.getName();
        boolean handled = switch (getter) {
            case "getInt", "getLong", "getShort", "getFloat", "getDouble",
                 "getBigDecimal", "getString", "getNString" -> true;
            case "getObject" -> args.length == 1
                || args[1] instanceof Class<?> type
                && (type.isAssignableFrom(Integer.class) || type == String.class);
            default -> false;
        };
        if (!handled) {
            return SKIP;
        }
        int jdbcType = jdbcTypeFor(columns.getString(TYPE_NAME_COLUMN));
        // Скрытое чтение TYPE_NAME выставило wasNull делегата по чужой
        // колонке; перечитываем настоящую DATA_TYPE — в ней всегда число,
        // и wasNull() после синтезированного значения честно вернёт false.
        columns.getInt(DATA_TYPE_COLUMN);
        return switch (getter) {
            case "getLong" -> (long) jdbcType;
            case "getShort" -> (short) jdbcType;
            case "getFloat" -> (float) jdbcType;
            case "getDouble" -> (double) jdbcType;
            case "getBigDecimal" -> BigDecimal.valueOf(jdbcType);
            case "getString", "getNString" -> String.valueOf(jdbcType);
            case "getObject" -> args.length == 2 && args[1] == String.class
                ? String.valueOf(jdbcType)
                : (Object) jdbcType;
            default -> jdbcType; // getInt
        };
    }

    private static Object typeNameValue(Method method, Object[] args, ResultSet columns) throws Throwable {
        switch (method.getName()) {
            case "getString":
            case "getNString":
                return typeNameFor(columns.getString(TYPE_NAME_COLUMN));
            case "getObject":
                if (args.length == 1
                    || args[1] instanceof Class<?> type && type.isAssignableFrom(String.class)) {
                    return typeNameFor(columns.getString(TYPE_NAME_COLUMN));
                }
                return SKIP;
            default:
                return SKIP;
        }
    }

    /**
     * Прокси сравнивается по идентичности — по той же причине, что
     * и соединение в ShimDriver.AutoCommitEmulation.
     */
    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args, String label) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            default:
                return label;
        }
    }

    private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
