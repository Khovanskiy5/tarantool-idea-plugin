---@meta

--[[
Ручные описания типов для файберов — кооперативных потоков Tarantool.

Объекты файберов, условных переменных и каналов создаются в рантайме,
интроспекция их не видит, поэтому методы описаны здесь.
]]

---Файбер — кооперативный поток выполнения.
---@class fiber.object
---@field storage table хранилище, живущее вместе с файбером
local fiber_object = {}

---Числовой идентификатор файбера.
---@return number
function fiber_object:id() end

---Читает или задаёт имя файбера.
---@param name? string
---@param options? {truncate?: boolean}
---@return string?
function fiber_object:name(name, options) end

---Состояние файбера: 'running', 'suspended', 'ready' или 'dead'.
---@return string
function fiber_object:status() end

---Просит файбер завершиться в ближайшей точке отмены.
function fiber_object:cancel() end

---Дожидается завершения файбера. Требует set_joinable(true).
---@param timeout? number
---@return boolean success, any result
function fiber_object:join(timeout) end

---Разрешает дожидаться файбера через join.
---@param is_joinable boolean
function fiber_object:set_joinable(is_joinable) end

---Пробуждает спящий файбер.
function fiber_object:wakeup() end

---Немедленно завершает файбер.
function fiber_object:kill() end

---Ограничивает время непрерывного выполнения файбера.
---@param limit number|{warn: number, err: number}
function fiber_object:set_max_slice(limit) end

---Задаёт остаток времени выполнения для текущего кванта.
---@param limit number|{warn: number, err: number}
function fiber_object:set_slice(limit) end

---Условная переменная: точка синхронизации между файберами.
---@class fiber.cond
local fiber_cond = {}

---Засыпает до сигнала или до истечения таймаута.
---@param timeout? number
---@return boolean пробуждение произошло по сигналу
function fiber_cond:wait(timeout) end

---Будит один ожидающий файбер.
function fiber_cond:signal() end

---Будит все ожидающие файберы.
function fiber_cond:broadcast() end

---Канал для передачи значений между файберами.
---@class fiber.channel
local fiber_channel = {}

---Кладёт значение в канал, при заполненном канале ждёт места.
---@param value any
---@param timeout? number
---@return boolean значение помещено в канал
function fiber_channel:put(value, timeout) end

---Забирает значение из канала, при пустом канале ждёт появления.
---@param timeout? number
---@return any
function fiber_channel:get(timeout) end

---@return boolean
function fiber_channel:is_empty() end

---@return boolean
function fiber_channel:is_full() end

---@return boolean
function fiber_channel:is_closed() end

---Число значений, лежащих в канале.
---@return number
function fiber_channel:count() end

---@return boolean
function fiber_channel:has_readers() end

---@return boolean
function fiber_channel:has_writers() end

---Закрывает канал: все ожидающие файберы получают nil.
function fiber_channel:close() end

---@class fiber
local fiber = {}

---Создаёт файбер и немедленно передаёт ему управление.
---@param fn fun(...): any
---@param ... any аргументы, передаваемые в fn
---@return fiber.object
function fiber.create(fn, ...) end

---Создаёт файбер, но не запускает его до ближайшей передачи управления.
---@param fn fun(...): any
---@param ... any
---@return fiber.object
function fiber.new(fn, ...) end

---Текущий файбер.
---@return fiber.object
function fiber.self() end

---Файбер по идентификатору.
---@param id number
---@return fiber.object?
function fiber.find(id) end

---Усыпляет текущий файбер и передаёт управление планировщику.
---@param timeout number секунды, дробное значение допустимо
function fiber.sleep(timeout) end

---Передаёт управление планировщику, не засыпая.
function fiber.yield() end

---Точка отмены: бросает ошибку, если файбер попросили завершиться.
function fiber.testcancel() end

---Завершает файбер по идентификатору.
---@param id number
function fiber.kill(id) end

---Состояние файбера.
---@param fiber? fiber.object
---@return string
function fiber.status(fiber) end

---Информация обо всех живых файберах.
---@param options? {backtrace?: boolean, bt?: boolean}
---@return table
function fiber.info(options) end

---Статистика потребления процессорного времени по файберам.
---@return table
function fiber.top() end

---Создаёт условную переменную.
---@return fiber.cond
function fiber.cond() end

---Создаёт канал заданной ёмкости.
---@param capacity? number 0 — небуферизованный канал
---@return fiber.channel
function fiber.channel(capacity) end

---Монотонные часы файбера, секунды.
---@return number
function fiber.clock() end

---Монотонные часы файбера, микросекунды.
---@return number
function fiber.clock64() end

---Системное время, секунды.
---@return number
function fiber.time() end

---Системное время, микросекунды.
---@return number
function fiber.time64() end
