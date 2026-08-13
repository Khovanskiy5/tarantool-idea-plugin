---@meta

--[[
Ручные описания типов для net.box — клиента бинарного протокола Tarantool,
и для журналирования через модуль log.
]]

---Опции сетевого вызова.
---@class netbox.request.options
---@field timeout? number
---@field buffer? userdata
---@field is_async? boolean вернуть future вместо результата
---@field on_push? fun(message: any)
---@field return_raw? boolean

---Отложенный результат асинхронного вызова.
---@class netbox.future
local future = {}

---Дожидается результата.
---@param timeout? number
---@return any result, any error
function future:wait_result(timeout) end

---Проверяет готовность результата, не блокируя файбер.
---@return boolean
function future:is_ready() end

---Забирает готовый результат, не дожидаясь.
---@return any
function future:result() end

---Отменяет запрос.
function future:discard() end

---Соединение с удалённым инстансом Tarantool.
---@class netbox.connection
---@field space table<string, box.space.object> спейсы удалённого инстанса
---@field state string 'active', 'error', 'closed', ...
---@field error string? текст последней ошибки
---@field peer_uuid string?
local connection = {}

---Вызывает удалённую функцию.
---@param function_name string
---@param args? any[]
---@param options? netbox.request.options
---@return any
function connection:call(function_name, args, options) end

---Выполняет Lua-код на удалённом инстансе.
---@param code string
---@param args? any[]
---@param options? netbox.request.options
---@return any
function connection:eval(code, args, options) end

---Выполняет SQL-запрос.
---@param query string
---@param parameters? any[]
---@param sql_options? table
---@param options? netbox.request.options
---@return table
function connection:execute(query, parameters, sql_options, options) end

---Проверяет доступность инстанса.
---@param options? {timeout?: number}
---@return boolean
function connection:ping(options) end

---Дожидается установки соединения.
---@param timeout? number
---@return boolean
function connection:wait_connected(timeout) end

---Дожидается перехода соединения в одно из состояний.
---@param state string|table<string, boolean>
---@param timeout? number
---@return boolean
function connection:wait_state(state, timeout) end

---@return boolean
function connection:is_connected() end

---Закрывает соединение.
function connection:close() end

---Создаёт поток — последовательность запросов с общей транзакцией.
---@return netbox.stream
function connection:new_stream() end

---Ставит обработчик подключения.
---@param trigger? fun(connection: netbox.connection)
---@param old_trigger? function
function connection:on_connect(trigger, old_trigger) end

---Ставит обработчик разрыва соединения.
---@param trigger? fun(connection: netbox.connection)
---@param old_trigger? function
function connection:on_disconnect(trigger, old_trigger) end

---Ставит обработчик перезагрузки схемы.
---@param trigger? fun(connection: netbox.connection)
---@param old_trigger? function
function connection:on_schema_reload(trigger, old_trigger) end

---Поток запросов: в его рамках можно вести транзакцию.
---@class netbox.stream: netbox.connection
local stream = {}

---Открывает транзакцию в потоке.
---@param options? {timeout?: number, txn_isolation?: string}
function stream:begin(options) end

---Фиксирует транзакцию потока.
---@param options? {is_sync?: boolean}
function stream:commit(options) end

---Откатывает транзакцию потока.
function stream:rollback() end

---@class net.box
local net_box = {}

---Устанавливает соединение с удалённым инстансом.
---@param uri string|number адрес вида 'user:pass@host:port' или порт
---@param options? {user?: string, password?: string, wait_connected?: boolean|number, reconnect_after?: number, connect_timeout?: number, fetch_schema?: boolean, required_protocol_version?: number}
---@return netbox.connection
function net_box.connect(uri, options) end

---Синоним connect.
---@param uri string|number
---@param options? table
---@return netbox.connection
function net_box.new(uri, options) end

--=============================================================================
-- Журналирование
--=============================================================================

---@class log
local log = {}

---@param message any формат в стиле string.format либо произвольное значение
---@param ... any
function log.info(message, ...) end

---@param message any
---@param ... any
function log.warn(message, ...) end

---@param message any
---@param ... any
function log.error(message, ...) end

---@param message any
---@param ... any
function log.debug(message, ...) end

---@param message any
---@param ... any
function log.verbose(message, ...) end

---Создаёт именованный журнал: имя попадает в каждую запись.
---@param name string
---@return log
function log.new(name) end

---Текущий уровень журналирования.
---@return number
function log.level() end

---Идентификатор процесса, пишущего журнал.
---@return number
function log.pid() end
