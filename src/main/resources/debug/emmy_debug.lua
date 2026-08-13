--[[
Подключение отладчика EmmyLua к процессу Tarantool.

Отладчик — нативная библиотека emmy_core, которая поставляется вместе
с плагином EmmyLua2 для IntelliJ IDEA. Библиотека подгружается в LuaJIT
Tarantool и принимает подключение IDE по TCP.

Важно: работает только режим, в котором слушает процесс, а подключается IDE
(в конфигурации IDE — «Tcp ( IDE connect debugger )»). Обратный режим
(emmy_core.tcpConnect, «Tcp ( Debugger connect IDE )») роняет Tarantool
с LuajitError, поэтому здесь не используется.

Порядок работы:
  1. В код приложения добавляется строка
       require('emmy_debug').attach_if_requested()
  2. Инстанс запускается с переменной окружения TARANTOOL_DEBUG:
       TARANTOOL_DEBUG=1 — отладчик включается в каждом инстансе;
       TARANTOOL_DEBUG=<имя инстанса> — только в названном (для кластера:
       иначе все инстансы боролись бы за один порт).
     Порт по умолчанию 9966, переопределяется переменной EMMY_PORT.
  3. В IDE запускается конфигурация «Tarantool: attach debugger» (Debug).
  4. Точки останова срабатывают в коде, выполняющемся после подключения:
     в функциях, вызываемых по запросам, триггерах, файберах.

Каталог с библиотекой ищется автоматически среди установленных версий IDEA;
переопределить путь можно переменной EMMY_CORE_DIR.
]]

local fio = require('fio')

local DEFAULT_HOST = '127.0.0.1'
local DEFAULT_PORT = 9966

--- Ищет каталог с emmy_core среди каталогов плагинов IntelliJ IDEA.
--- Версии перебираются от новых к старым.
---@return string?
local function find_debugger_dir()
    local override = os.getenv('EMMY_CORE_DIR')
    if override then
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

--- Открывает порт отладчика и придерживает выполнение, давая IDE время
--- подключиться. Пауза нужна для коротких скриптов: emmy_core.waitIDE()
--- в сборке 1.9.0 возвращается сразу и ожидание не обеспечивает.
---@param options? {host?: string, port?: number, wait_seconds?: number}
---@return boolean success, string? error
function debugger.attach(options)
    options = options or {}

    local dir = find_debugger_dir()
    if not dir then
        return false, 'каталог emmy_core не найден; задайте EMMY_CORE_DIR'
    end

    package.cpath = ('%s/?.dylib;%s/?.so;%s'):format(dir, dir, package.cpath)

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
function debugger.attach_if_requested(options)
    local flag = os.getenv('TARANTOOL_DEBUG')
    if flag == nil or flag == '' or flag == '0' or flag == 'false' then
        return
    end
    if flag ~= '1' and flag ~= 'true' and flag ~= instance_name() then
        return
    end

    local log = require('log')
    local port = tonumber(os.getenv('EMMY_PORT')) or DEFAULT_PORT

    local ok, err = debugger.attach(options)
    if ok then
        log.info('отладчик слушает порт %d, подключайтесь из IDE', port)
    else
        log.warn('отладчик EmmyLua не запущен: %s', err)
    end
end

return debugger
