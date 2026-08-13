---@meta

--[[
Ручные описания типов для объектов box: спейсов, индексов и кортежей.

Интроспекция (tools/gen_stubs.lua) снимает только имена и вариадические
сигнатуры, а объекты спейсов вообще создаются в рантайме, поэтому сигнатуры
самых ходовых методов описаны здесь вручную. Классы с одинаковым именем
сливаются с автогенерированными, а функции, описанные тут, из автогенерации
исключаются.
]]

---@alias box.tuple.type box.tuple.object|table
---@alias box.key any|any[]

---Опции выборки.
---@class box.select.options
---@field iterator? string|number направление обхода: 'EQ', 'GT', 'REQ', ...
---@field limit? number максимум кортежей в ответе
---@field offset? number сколько кортежей пропустить
---@field after? box.tuple.type|box.key продолжить после указанной позиции
---@field fetch_pos? boolean вернуть вместе с данными позицию для пагинации

---Описание одного поля формата спейса.
---@class box.field.format
---@field name string
---@field type? string 'unsigned', 'string', 'number', 'any', ...
---@field is_nullable? boolean

---Опции создания спейса.
---@class box.space.create.options
---@field engine? string 'memtx' (по умолчанию) или 'vinyl'
---@field field_count? number фиксированное число полей, 0 — без ограничения
---@field format? box.field.format[]
---@field id? number
---@field if_not_exists? boolean
---@field is_local? boolean спейс не реплицируется
---@field temporary? boolean содержимое не пишется в WAL и теряется при рестарте
---@field is_sync? boolean синхронная репликация

---Опции создания индекса.
---@class box.index.create.options
---@field type? string 'TREE' (по умолчанию), 'HASH', 'RTREE', 'BITSET'
---@field id? number
---@field unique? boolean
---@field if_not_exists? boolean
---@field parts? table[] описание полей индекса
---@field sequence? string|number|boolean
---@field func? string имя функции для функционального индекса

--=============================================================================
-- Кортеж
--=============================================================================

---Кортеж — неизменяемый упорядоченный набор полей.
---Поля доступны по номеру (с единицы) и по имени, если у спейса задан формат.
---@class box.tuple.object
---@field [integer] any
---@field [string] any
local tuple = {}

---Размер кортежа в байтах в формате MsgPack.
---@return number
function tuple:bsize() end

---Преобразует кортеж в обычную Lua-таблицу.
---@param start_index? number
---@param end_index? number
---@return any[]
function tuple:totable(start_index, end_index) end

---То же, что totable, но возвращает поля списком значений.
---@return any ...
function tuple:unpack() end

---Преобразует кортеж в таблицу «имя поля -> значение».
---Требует формата спейса.
---@param options? {names_only?: boolean}
---@return table<string, any>
function tuple:tomap(options) end

---Возвращает новый кортеж с применёнными операциями обновления.
---@param operations table[] список вида {{'=', 2, 'new value'}, {'+', 3, 1}}
---@return box.tuple.object
function tuple:update(operations) end

---Обновляет кортеж на месте, без создания копии.
---@param operations table[]
---@return box.tuple.object
function tuple:upsert(operations) end

---Заменяет диапазон полей, начиная с start_index, на переданные значения.
---@param start_index number
---@param fields_to_remove number
---@param ... any
---@return box.tuple.object
function tuple:transform(start_index, fields_to_remove, ...) end

---Номер первого поля с указанным значением.
---@param field_number? number с какого поля начинать поиск
---@param search_value any
---@return number?
function tuple:find(field_number, search_value) end

---Номера всех полей с указанным значением.
---@param field_number? number
---@param search_value any
---@return number[]
function tuple:findall(field_number, search_value) end

---Итератор по полям кортежа.
---@return fun(): number, any
function tuple:pairs() end

--=============================================================================
-- Индекс
--=============================================================================

---Индекс спейса.
---@class box.index.object
---@field id number
---@field name string
---@field space_id number
---@field type string
---@field unique boolean
---@field parts table[]
local index = {}

---Выбирает кортежи по ключу.
---@param key? box.key
---@param options? box.select.options
---@return box.tuple.object[]
function index:select(key, options) end

---Возвращает единственный кортеж по ключу либо nil.
---@param key box.key
---@return box.tuple.object?
function index:get(key) end

---Первый кортеж в порядке индекса (не больше указанного ключа).
---@param key? box.key
---@return box.tuple.object?
function index:min(key) end

---Последний кортеж в порядке индекса (не меньше указанного ключа).
---@param key? box.key
---@return box.tuple.object?
function index:max(key) end

---Случайный кортеж. Поддерживается только индексами TREE и HASH.
---@param seed number
---@return box.tuple.object?
function index:random(seed) end

---Количество кортежей, подходящих под ключ.
---@param key? box.key
---@param options? box.select.options
---@return number
function index:count(key, options) end

---Обновляет кортеж, найденный по ключу.
---@param key box.key
---@param operations table[]
---@return box.tuple.object?
function index:update(key, operations) end

---Удаляет кортеж по ключу.
---@param key box.key
---@return box.tuple.object?
function index:delete(key) end

---Итератор по кортежам индекса — работает в связке с модулем fun.
---@param key? box.key
---@param options? box.select.options|string
---@return fun(): box.tuple.object?
function index:pairs(key, options) end

---Число кортежей в индексе.
---@return number
function index:len() end

---Размер индекса в байтах.
---@return number
function index:bsize() end

---Меняет параметры индекса.
---@param options box.index.create.options
function index:alter(options) end

---Удаляет индекс.
function index:drop() end

---Переименовывает индекс.
---@param name string
function index:rename(name) end

---Статистика по индексу (актуально для vinyl).
---@return table
function index:stat() end

---Запускает уплотнение (только vinyl).
function index:compact() end

--=============================================================================
-- Спейс
--=============================================================================

---Спейс — таблица с кортежами.
---@class box.space.object
---@field id number
---@field name string
---@field engine string
---@field enabled boolean
---@field field_count number
---@field is_local boolean
---@field temporary boolean
---@field index table<string|number, box.index.object>
local space = {}

---Выбирает кортежи по ключу первичного индекса.
---@param key? box.key
---@param options? box.select.options
---@return box.tuple.object[]
function space:select(key, options) end

---Возвращает единственный кортеж по ключу первичного индекса либо nil.
---@param key box.key
---@return box.tuple.object?
function space:get(key) end

---Вставляет кортеж. Бросает ошибку, если ключ уже занят.
---@param tuple box.tuple.type
---@return box.tuple.object
function space:insert(tuple) end

---Вставляет кортеж, перезаписывая существующий с тем же ключом.
---@param tuple box.tuple.type
---@return box.tuple.object
function space:replace(tuple) end

---Синоним replace.
---@param tuple box.tuple.type
---@return box.tuple.object
function space:put(tuple) end

---Обновляет кортеж, найденный по ключу первичного индекса.
---@param key box.key
---@param operations table[] например {{'=', 2, 'new'}, {'+', 3, 1}}
---@return box.tuple.object?
function space:update(key, operations) end

---Вставляет кортеж либо применяет операции к существующему.
---Ничего не возвращает и не проверяет результат — обновление отложенное.
---@param tuple box.tuple.type
---@param operations table[]
function space:upsert(tuple, operations) end

---Удаляет кортеж по ключу первичного индекса.
---@param key box.key
---@return box.tuple.object?
function space:delete(key) end

---Итератор по кортежам спейса — работает в связке с модулем fun.
---@param key? box.key
---@param options? box.select.options|string
---@return fun(): box.tuple.object?
function space:pairs(key, options) end

---Количество кортежей, подходящих под ключ.
---@param key? box.key
---@param options? box.select.options
---@return number
function space:count(key, options) end

---Число кортежей в спейсе.
---@return number
function space:len() end

---Размер данных спейса в байтах.
---@return number
function space:bsize() end

---Создаёт индекс.
---@param name string
---@param options? box.index.create.options
---@return box.index.object
function space:create_index(name, options) end

---Удаляет все кортежи. В отличие от drop, сам спейс сохраняется.
function space:truncate() end

---Удаляет спейс вместе с индексами и данными.
function space:drop() end

---Переименовывает спейс.
---@param name string
function space:rename(name) end

---Читает или задаёт формат полей спейса.
---@param format? box.field.format[]
---@return box.field.format[]
function space:format(format) end

---Меняет параметры спейса.
---@param options box.space.create.options
function space:alter(options) end

---Собирает кортеж из таблицы «имя поля -> значение» по формату спейса.
---@param map table<string, any>
---@param options? {table?: boolean}
---@return box.tuple.object|table
function space:frommap(map, options) end

---Вставляет кортеж, подставив в первое поле следующее значение счётчика.
---@param tuple any[]
---@return box.tuple.object
function space:auto_increment(tuple) end

---Ставит или снимает триггер, срабатывающий после изменения кортежа.
---@param trigger? fun(old: box.tuple.object?, new: box.tuple.object?, space_name: string, operation: string)
---@param old_trigger? function
---@return function[]
function space:on_replace(trigger, old_trigger) end

---Ставит триггер, срабатывающий до изменения кортежа: он может подменить
---или отменить запись, вернув другой кортеж либо nil.
---@param trigger? fun(old: box.tuple.object?, new: box.tuple.object?, space_name: string, operation: string): box.tuple.object?
---@param old_trigger? function
---@return function[]
function space:before_replace(trigger, old_trigger) end

---Включает или выключает триггеры спейса.
---@param enabled boolean
function space:run_triggers(enabled) end

--=============================================================================
-- Обращение к спейсам и схеме
--=============================================================================

---Спейсы доступны по имени и по числовому идентификатору.
---@class box.space
---@field [string] box.space.object
---@field [integer] box.space.object

---@class box.schema.space
local schema_space = {}

---Создаёт спейс.
---@param name string
---@param options? box.space.create.options
---@return box.space.object
function schema_space.create(name, options) end

---@class box.schema.user
local schema_user = {}

---Создаёт пользователя.
---@param name string
---@param options? {password?: string, if_not_exists?: boolean}
function schema_user.create(name, options) end

---Выдаёт права пользователю.
---@param name string
---@param privileges string 'read', 'write', 'execute', 'create', 'super', ...
---@param object_type? string 'space', 'function', 'universe', ...
---@param object_name? string
---@param options? {grantor?: string|number, if_not_exists?: boolean}
function schema_user.grant(name, privileges, object_type, object_name, options) end

---Отзывает права.
---@param name string
---@param privileges string
---@param object_type? string
---@param object_name? string
---@param options? {if_exists?: boolean}
function schema_user.revoke(name, privileges, object_type, object_name, options) end

---Меняет пароль пользователя.
---@param name string
---@param password? string
function schema_user.passwd(name, password) end

---@class box.schema.func
local schema_func = {}

---Регистрирует функцию в схеме.
---@param name string
---@param options? {if_not_exists?: boolean, language?: string, body?: string, is_deterministic?: boolean, is_sandboxed?: boolean, is_multikey?: boolean, takes_raw_args?: boolean}
function schema_func.create(name, options) end

--=============================================================================
-- Транзакции
--=============================================================================

---@class box
box = {}

---Открывает транзакцию.
---@param options? {txn_isolation?: string, timeout?: number}
function box.begin(options) end

---Фиксирует транзакцию.
function box.commit() end

---Откатывает транзакцию.
function box.rollback() end

---Выполняет функцию в одной транзакции.
---@generic T
---@param tx_function fun(...): T
---@param ... any
---@return T
function box.atomic(tx_function, ...) end

---Выполняет функцию ровно один раз за всю историю инстанса.
---Результат факта выполнения хранится в системном спейсе.
---@param key string
---@param fn fun(...): any
---@param ... any
function box.once(key, fn, ...) end

---Создаёт точку сохранения внутри транзакции.
---@return table
function box.savepoint() end

---Откатывает транзакцию до точки сохранения.
---@param savepoint table
function box.rollback_to_savepoint(savepoint) end

---Признак того, что выполнение идёт внутри транзакции.
---@return boolean
function box.is_in_txn() end

---Создаёт снимок данных (.snap).
function box.snapshot() end

---Выполняет SQL-запрос.
---@param query string
---@param parameters? any[]
---@return table
function box.execute(query, parameters) end
