--[[
Загрузчик графического отладчика, который подставляет плагин Tarantool.
Пользовательский код при этом не меняется — в проекте этого файла нет,
он живёт в системном каталоге IDE.

Два способа запуска, оба выставляет сам плагин:

  * скрипт   — tarantool -e "dofile('…/emmy_bootstrap.lua')" script.lua
               (чанк -e выполняется до скрипта);
  * кластер  — TT_APP_FILE=…/emmy_bootstrap.lua tt start
               (переменная перекрывает app.file кластерной конфигурации;
               настоящее приложение загружается отсюда же).

Порядок: открыть порт → сообщить IDE, что порт открыт (файл-маркер) →
дождаться подключения IDE (второй файл-маркер) → отдать управление
приложению. Ожидание нужно, чтобы точки останова работали и в стартовом
коде: к моменту первой строки приложения отладчик уже подключён.
Если IDE не подключилась за отведённое время, приложение всё равно
стартует — отладка не должна мешать запуску.

Переменные окружения (их выставляет плагин):
  EMMY_CORE_DIR                    каталог нативной библиотеки emmy_core
  EMMY_HOST, EMMY_PORT             адрес порта отладчика
  TARANTOOL_DEBUG_INSTANCE         кто открывает порт (пусто — любой)
  TARANTOOL_DEBUG_LISTEN_MARKER    файл: порт открыт
  TARANTOOL_DEBUG_READY_MARKER     файл: IDE подключилась
  TARANTOOL_DEBUG_TIMEOUT          сколько секунд ждать IDE
  TARANTOOL_DEBUG_APP_FILE         настоящий app.file кластера
  TARANTOOL_DEBUG_APP_MODULE       настоящий app.module кластера
]]

local fio = require('fio')
local fiber = require('fiber')
local log = require('log')

local POLL_SECONDS = 0.05
local DEFAULT_TIMEOUT = 60

---@param name string
---@return string?
local function env(name)
    local value = os.getenv(name)
    if value == nil or value == '' then
        return nil
    end
    return value
end

-- Хелпер лежит рядом с этим файлом: плагин распаковывает оба вместе.
-- Загружается он по абсолютному пути, а не через require: в проекте
-- может лежать своя копия emmy_debug.lua (её кладёт действие «Настроить
-- Emmy-отладчик»), и загрузчики Tarantool нашли бы именно её — возможно,
-- другой версии. Результат кладётся в package.loaded под тем же именем,
-- поэтому require('emmy_debug') в коде приложения вернёт этот же модуль
-- и второй раз порт открывать не станет.
local here = debug.getinfo(1, 'S').source:match('^@(.+)[/\\][^/\\]+$')
local ok_helper, emmy = false, nil
if here ~= nil then
    package.path = ('%s/?.lua;%s'):format(here, package.path)
    ok_helper, emmy = pcall(dofile, here .. '/emmy_debug.lua')
    if ok_helper then
        package.loaded['emmy_debug'] = emmy
    end
end

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

--- В кластере порт открывает ровно один инстанс: остальные боролись бы
--- за тот же порт.
---@return boolean
local function selected_for_debug()
    local wanted = env('TARANTOOL_DEBUG_INSTANCE')
    return wanted == nil or wanted == instance_name()
end

---@param path string
local function touch(path)
    local handle = fio.open(path, { 'O_WRONLY', 'O_CREAT', 'O_TRUNC' }, tonumber('644', 8))
    if handle ~= nil then
        handle:write(tostring(instance_name() or 'tarantool'))
        handle:close()
    end
end

--- Ждёт появления файла-маркера. Возвращает false, если не дождались.
---@param path string
---@param timeout number
---@return boolean
local function wait_for(path, timeout)
    local deadline = fiber.clock() + timeout
    while fiber.clock() < deadline do
        if fio.path.exists(path) then
            return true
        end
        fiber.sleep(POLL_SECONDS)
    end
    return false
end

local function attach()
    if not ok_helper then
        log.warn('отладчик не запущен: не удалось загрузить emmy_debug (%s)', tostring(emmy))
        return
    end
    if not selected_for_debug() then
        return
    end

    local ok, err = emmy.attach({
        host = env('EMMY_HOST'),
        port = tonumber(env('EMMY_PORT')),
    })
    emmy.report(ok, err)
    if not ok then
        return
    end

    local listen_marker = env('TARANTOOL_DEBUG_LISTEN_MARKER')
    if listen_marker ~= nil then
        touch(listen_marker)
    end

    local ready_marker = env('TARANTOOL_DEBUG_READY_MARKER')
    if ready_marker ~= nil then
        local timeout = tonumber(env('TARANTOOL_DEBUG_TIMEOUT')) or DEFAULT_TIMEOUT
        if wait_for(ready_marker, timeout) then
            log.info('IDE подключилась, продолжаем запуск')
        else
            log.warn('IDE не подключилась за %d с — запуск продолжается без ожидания', timeout)
        end
    end
end

--- Загружает настоящее приложение кластера. В режиме скрипта переменные
--- не заданы: скрипт запустит сам интерпретатор после чанка -e.
local function run_application()
    local app_file = env('TARANTOOL_DEBUG_APP_FILE')
    if app_file ~= nil then
        dofile(app_file)
        return
    end
    local app_module = env('TARANTOOL_DEBUG_APP_MODULE')
    if app_module ~= nil then
        require(app_module)
    end
end

local ok_attach, attach_error = pcall(attach)
if not ok_attach then
    -- Сбой отладчика не должен мешать приложению стартовать.
    log.warn('отладчик не запущен: %s', tostring(attach_error))
end

run_application()
