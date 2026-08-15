--[[
Подключение отладчика EmmyLua к процессу Tarantool.

Отладчик — нативная библиотека emmy_core, которая поставляется вместе
с плагином EmmyLua2 для IntelliJ IDEA. Библиотека подгружается в LuaJIT
Tarantool и принимает подключение IDE по TCP.

Важно: работает только режим, в котором слушает процесс, а подключается IDE
(в конфигурации IDE — «Tcp ( IDE connect debugger )»). Обратный режим
(emmy_core.tcpConnect, «Tcp ( Debugger connect IDE )») роняет Tarantool
с LuajitError, поэтому здесь не используется.

Обычно этот файл вызывать не нужно: плагин запускает отладку сам —
кнопкой Debug у конфигурации «Tarantool» (скрипты) и кнопкой
«Запустить с отладчиком» на панели инстансов (кластер). Хелпер остаётся
для запусков вне IDE и для приложений, которые хотят открывать порт сами:

  * `attach_from_config()` — по секции app.cfg.debugger в config.yaml;
  * `attach_if_requested()` — по переменной окружения TARANTOOL_DEBUG
    (`1`/`true` — в любом инстансе, иначе значение считается именем
    инстанса: в кластере порт открывает ровно один);
  * `attach()` — безусловно.

Порт по умолчанию 9966, переопределяется переменной EMMY_PORT. Каталог
с библиотекой ищется среди установленных версий IDE; переопределить —
переменной EMMY_CORE_DIR (её и выставляет плагин).
]]

local fio = require('fio')

local DEFAULT_HOST = '127.0.0.1'
local DEFAULT_PORT = 9966
local MAX_DEPTH = 5
local MAX_ITEMS = 200

--- Ищет каталог с emmy_core среди каталогов плагинов IntelliJ IDEA.
--- Версии перебираются от новых к старым.
---@return string?
local function find_debugger_dir()
    local override = os.getenv('EMMY_CORE_DIR')
    if override ~= nil and override ~= '' then
        return override
    end

    local home = os.getenv('HOME')
    if not home then
        return nil
    end

    local arch = jit.arch == 'arm64' and 'arm64' or 'x64'
    local roots = {
        home .. '/Library/Application Support/JetBrains',
        home .. '/.local/share/JetBrains',
        home .. '/.config/JetBrains',
    }

    local candidates = {}
    for _, root in ipairs(roots) do
        if fio.path.is_dir(root) then
            for _, entry in ipairs(fio.listdir(root)) do
                local base = ('%s/%s/plugins/IntelliJ-EmmyLua2/debugger/emmy'):format(root, entry)
                for _, platform in ipairs({ 'mac/' .. arch, 'linux', 'mac' }) do
                    if fio.path.is_dir(base .. '/' .. platform) then
                        candidates[#candidates + 1] = { name = entry, path = base .. '/' .. platform }
                        break
                    end
                end
            end
        end
    end

    -- Имена каталогов версионные (IntelliJIdea2026.1), поэтому обратная
    -- сортировка по строке даёт самую свежую установку.
    table.sort(candidates, function(a, b) return a.name > b.name end)
    return candidates[1] and candidates[1].path or nil
end

--- Имя текущего инстанса: доступно после box.cfg (модули приложения
--- в Tarantool 3 выполняются уже после применения конфигурации).
---@return string?
local function instance_name()
    local ok, name = pcall(function()
        return box.info.name
    end)
    if ok and name ~= nil then
        return name
    end
    return os.getenv('TT_INSTANCE_NAME')
end

local debugger = {}

-- Порт открывается один раз на процесс: загрузчик IDE и код приложения
-- могут звать attach независимо друг от друга, и второй вызов не должен
-- ни падать, ни занимать второй порт.
local attached = false

local convert

--- Разворачивает тапл в таблицу с именами полей, если у спейса задан format.
---@param tuple box.tuple
---@param depth number
---@return table
local function convert_tuple(tuple, depth)
    local ok, map = pcall(tuple.tomap, tuple, { names_only = true })
    if ok and next(map) ~= nil then
        return convert(map, depth + 1)
    end
    return convert(tuple:totable(), depth + 1)
end

---@param value any
---@param depth number
---@return any
function convert(value, depth)
    if depth > MAX_DEPTH then
        return '…'
    end

    local kind = type(value)
    if kind == 'cdata' then
        if box ~= nil and box.tuple ~= nil and box.tuple.is(value) then
            return convert_tuple(value, depth)
        end
        -- объект ошибки Tarantool разворачивается методом unpack
        local unpacked_ok, unpacked = pcall(function() return value:unpack() end)
        if unpacked_ok and type(unpacked) == 'table' then
            unpacked.trace = nil -- трассировка в панели только шумит
            return convert(unpacked, depth + 1)
        end
        -- decimal, uuid, datetime, interval: у всех есть __tostring
        local text_ok, text = pcall(tostring, value)
        return text_ok and text or '<cdata>'
    end

    if kind ~= 'table' then
        return value
    end

    local copy = {}
    local count = 0
    for key, item in pairs(value) do
        count = count + 1
        if count > MAX_ITEMS then
            copy['…'] = ('показаны первые %d элементов'):format(MAX_ITEMS)
            break
        end
        copy[convert(key, depth + 1)] = convert(item, depth + 1)
    end
    return copy
end

--- Разворачивает значение Tarantool в вид, пригодный для панели переменных.
---
--- Отладчик показывает cdata пустым значением, а тапл, decimal, uuid,
--- datetime, interval и объект ошибки — это как раз cdata. Функция
--- превращает их в обычные таблицы и строки, которые панель рисует
--- деревом. Она же кладётся в глобальную D() — чтобы в списке Watches
--- писать коротко: D(tuple).
---@param value any
---@return any
function debugger.inspect(value)
    local ok, result = pcall(convert, value, 1)
    return ok and result or ('<не удалось развернуть: %s>'):format(result)
end

--- Открывает порт отладчика и придерживает выполнение, давая IDE время
--- подключиться. Пауза нужна для коротких скриптов: emmy_core.waitIDE()
--- в сборке 1.9.0 возвращается сразу и ожидание не обеспечивает.
---@param options? {host?: string, port?: number, wait_seconds?: number}
---@return boolean success, string? error
function debugger.attach(options)
    if attached then
        return true
    end
    options = options or {}

    local dir = find_debugger_dir()
    if not dir then
        return false, 'каталог emmy_core не найден; задайте EMMY_CORE_DIR'
    end

    package.cpath = ('%s/?.dylib;%s/?.so;%s/?.dll;%s'):format(dir, dir, dir, package.cpath)

    local loaded, core = pcall(require, 'emmy_core')
    if not loaded then
        return false, ('не удалось загрузить emmy_core из %s: %s'):format(dir, core)
    end

    local host = options.host or os.getenv('EMMY_HOST') or DEFAULT_HOST
    local port = tonumber(options.port or os.getenv('EMMY_PORT')) or DEFAULT_PORT

    local ok, result = pcall(core.tcpListen, host, port)
    if not ok then
        return false, ('не удалось открыть порт %s:%d: %s'):format(host, port, tostring(result))
    end
    if result == false then
        return false, ('порт %s:%d занят'):format(host, port)
    end

    core.waitIDE()
    attached = true

    -- D() в глобальных: панель Watches показывает таплы и прочие cdata.
    rawset(_G, 'D', debugger.inspect)

    local wait_seconds = tonumber(options.wait_seconds or os.getenv('EMMY_WAIT')) or 0
    if wait_seconds > 0 then
        require('fiber').sleep(wait_seconds)
    end
    return true
end

--- Открывает порт отладчика, только если запуск помечен переменной
--- TARANTOOL_DEBUG. Значение «1»/«true» включает отладчик безусловно,
--- любое другое значение трактуется как имя инстанса — в кластере порт
--- откроет только он. В обычном запуске функция не делает ничего.
---@param options? {host?: string, port?: number, wait_seconds?: number}
---@return boolean
function debugger.attach_if_requested(options)
    local flag = os.getenv('TARANTOOL_DEBUG')
    if flag == nil or flag == '' or flag == '0' or flag == 'false' then
        return false
    end
    if flag ~= '1' and flag ~= 'true' and flag ~= instance_name() then
        return false
    end
    local ok, err = debugger.attach(options)
    return debugger.report(ok, err, options)
end

--- Открывает порт по секции app.cfg.debugger кластерной конфигурации:
---
---     app:
---       file: 'src/app.lua'
---       cfg:
---         debugger:
---           enabled: true
---           instance: 'storage-001-a'   # кто именно открывает порт
---           port: 9966
---
--- Конфигурация редактируется со схемой и автодополнением, а инстанс
--- запускается обычной кнопкой — без переменных окружения.
---@return boolean
function debugger.attach_from_config()
    local ok_module, config = pcall(require, 'config')
    if not ok_module then
        return false
    end
    local ok_value, app_cfg = pcall(config.get, config, 'app.cfg')
    if not ok_value or app_cfg == nil or app_cfg.debugger == nil then
        return false
    end

    local settings = app_cfg.debugger
    if settings.enabled ~= true then
        return false
    end
    if settings.instance ~= nil and settings.instance ~= instance_name() then
        return false
    end

    local options = {
        host = settings.host,
        port = settings.port,
        wait_seconds = settings.wait_seconds,
    }
    local ok, err = debugger.attach(options)
    return debugger.report(ok, err, options)
end

--- Открыт ли уже порт отладчика в этом процессе.
---@return boolean
function debugger.is_attached()
    return attached
end

--- Пишет в журнал результат подключения: строку видно в панели логов.
---@param ok boolean
---@param err string?
---@param options table?
---@return boolean
function debugger.report(ok, err, options)
    local log = require('log')
    local port = tonumber((options or {}).port or os.getenv('EMMY_PORT')) or DEFAULT_PORT
    if ok then
        log.info('отладчик слушает порт %d, подключайтесь из IDE', port)
    else
        log.warn('отладчик EmmyLua не запущен: %s', err)
    end
    return ok
end

return debugger
