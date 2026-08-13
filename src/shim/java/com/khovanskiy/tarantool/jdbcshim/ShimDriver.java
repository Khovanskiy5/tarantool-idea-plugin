package com.khovanskiy.tarantool.jdbcshim;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Обёртка над драйвером Tarantool, совместимая с редактором данных IDE.
 *
 * Родной драйвер безусловно бросает исключение из setAutoCommit(false),
 * а редактор данных оборачивает сохранение транзакцией независимо от
 * режима Tx. Интерактивные транзакции Tarantool требуют IPROTO-streams,
 * которых в драйвере нет, поэтому обёртка эмулирует автокоммит-семантику:
 * каждая операция применяется сервером сразу, commit не делает ничего,
 * а rollback честно сообщает, что откат невозможен.
 *
 * Заодно обёртка чинит метаданные колонок: родной getColumns() отдаёт
 * DATA_TYPE = Types.OTHER для любого типа, из-за чего редактор данных
 * квотирует числа строками и сервер отвергает запрос — см.
 * {@link ColumnTypeMapping}.
 *
 * Подключение: Database → Data Sources → Drivers → Tarantool →
 * Driver Files: добавить jar этой обёртки; Class:
 * com.khovanskiy.tarantool.jdbcshim.ShimDriver.
 */
public final class ShimDriver implements Driver {

    private final Driver delegate;

    static {
        try {
            DriverManager.registerDriver(new ShimDriver());
        } catch (SQLException ignored) {
            // регистрация делегата уже прошла или недоступна — не критично:
            // IDE загружает класс драйвера напрямую, минуя DriverManager
        }
        deregisterRawDelegate();
    }

    /**
     * Статический инициализатор SQLDriver саморегистрирует его при загрузке
     * класса — это происходит в конструкторе обёртки, то есть раньше её
     * собственной регистрации. DriverManager отдаёт первый подошедший
     * драйвер, и сырой SQLDriver перехватывал бы jdbc:tarantool:// мимо
     * эмуляции автокоммита — убираем его из реестра, остаётся обёртка.
     */
    private static void deregisterRawDelegate() {
        java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver instanceof org.tarantool.jdbc.SQLDriver) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ignored) {
                    // не удалось — DriverManager лишь оставит лишний драйвер,
                    // прямые подключения через ShimDriver это не ломает
                }
            }
        }
    }

    public ShimDriver() throws SQLException {
        this.delegate = new org.tarantool.jdbc.SQLDriver();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        Connection connection = delegate.connect(url, info);
        if (connection == null) {
            return null;
        }
        return (Connection) Proxy.newProxyInstance(
            ShimDriver.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            new AutoCommitEmulation(connection)
        );
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    /**
     * Эмуляция автокоммита поверх соединения без транзакций.
     *
     * rollback не бросает исключение: IDE вызывает его и в служебных
     * сценариях (подготовка соединения, реакция на ошибку), и бросок
     * прерывал бы сохранение целиком. Вместо этого выставляется
     * SQLWarning — его видно в логе, а рабочий сценарий не ломается.
     */
    private static final class AutoCommitEmulation implements InvocationHandler {

        private final Connection real;
        private volatile boolean autoCommit = true;
        private volatile SQLWarning shimWarning;

        AutoCommitEmulation(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                // Прокси сравнивается по идентичности: делегирование equals
                // в real ломает рефлексивность (real.equals(proxy) == false),
                // и коллекции пулов перестают находить соединение.
                switch (method.getName()) {
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    default:
                        return "ShimConnection[" + real + "]";
                }
            }
            switch (method.getName()) {
                case "setAutoCommit":
                    // Сервер всегда в автокоммите; запоминаем только флаг.
                    autoCommit = (Boolean) args[0];
                    return null;
                case "getAutoCommit":
                    return autoCommit;
                case "commit":
                    // Операции уже применены сервером.
                    return null;
                case "rollback":
                    shimWarning = new SQLWarning(
                        "Tarantool: транзакции недоступны в этом драйвере — " +
                        "операции применяются сразу, откат не выполнен");
                    return null;
                case "getWarnings": {
                    SQLWarning own = shimWarning;
                    SQLWarning delegated = real.getWarnings();
                    if (own == null) {
                        return delegated;
                    }
                    if (delegated != null) {
                        own.setNextWarning(delegated);
                    }
                    return own;
                }
                case "clearWarnings":
                    shimWarning = null;
                    real.clearWarnings();
                    return null;
                case "getMetaData":
                    return ColumnTypeMapping.wrap(real.getMetaData(), (Connection) proxy);
                default:
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
            }
        }
    }
}
