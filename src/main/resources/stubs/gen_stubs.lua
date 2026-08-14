--[[
Генератор EmmyLua/LuaCATS-заглушек (stubs) для API Tarantool.

Дополняет встроенные курированные аннотации (.types/tarantool/bundled):
модули, покрытые бандлом, пропускаются, а остальные снимаются интроспекцией
с живого интерпретатора — точно под установленную версию: обходятся таблицы
встроенных модулей, для Lua-функций из отладочной информации достаются имена
параметров, для таблиц с метаметодом __call добавляется ---@overload.

Запуск:
    tarantool tools/gen_stubs.lua

Результат складывается в .types/tarantool/generated/. Каталог перезаписывается
целиком, править его руками бессмысленно — ручные уточнения типов живут
в .types/tarantool/manual/ и дополняют сгенерированные классы.
]]

local OUT_DIR = '.types/tarantool/generated'
local MANUAL_DIR = '.types/tarantool/manual'
local BUNDLED_LIBRARY_DIR = '.types/tarantool/bundled/Library'
local MAX_DEPTH = 3

-- Модули, попадающие в выгрузку. `global` — модуль виден как глобальная
-- переменная, `require` — доступен только через require().
local MODULES = {
    { name = 'box', kind = 'global' },
    { name = 'buffer', kind = 'require' },
    { name = 'checks', kind = 'require' },
    { name = 'clock', kind = 'require' },
    { name = 'compat', kind = 'require' },
    { name = 'config', kind = 'require' },
    { name = 'console', kind = 'require' },
    { name = 'crypto', kind = 'require' },
    { name = 'csv', kind = 'require' },
    { name = 'datetime', kind = 'require' },
    { name = 'decimal', kind = 'require' },
    { name = 'digest', kind = 'require' },
    { name = 'errno', kind = 'require' },
    { name = 'fiber', kind = 'require' },
    { name = 'fio', kind = 'require' },
    { name = 'fun', kind = 'require' },
    { name = 'http.client', kind = 'require' },
    { name = 'json', kind = 'require' },
    { name = 'key_def', kind = 'require' },
    { name = 'log', kind = 'require' },
    { name = 'merger', kind = 'require' },
    { name = 'metrics', kind = 'require' },
    { name = 'msgpack', kind = 'require' },
    { name = 'net.box', kind = 'require' },
    { name = 'popen', kind = 'require' },
    { name = 'socket', kind = 'require' },
    { name = 'swim', kind = 'require' },
    { name = 'tarantool', kind = 'require' },
    { name = 'uri', kind = 'require' },
    { name = 'utf8', kind = 'require' },
    { name = 'uuid', kind = 'require' },
    { name = 'varbinary', kind = 'require' },
    { name = 'xlog', kind = 'require' },
    { name = 'yaml', kind = 'require' },
}

-- Поля стандартной библиотеки LuaJIT: их описывает сам анализатор, повторное
-- объявление дало бы дубликаты в автодополнении. Для этих таблиц выгружается
-- только дельта — расширения, добавленные Tarantool (table.deepcopy и т.п.).
local STDLIB = {
    string = {
        'byte', 'char', 'dump', 'find', 'format', 'gmatch', 'gsub', 'len',
        'lower', 'match', 'rep', 'reverse', 'sub', 'upper',
    },
    table = { 'concat', 'insert', 'maxn', 'remove', 'sort' },
    os = {
        'clock', 'date', 'difftime', 'execute', 'exit', 'getenv', 'remove',
        'rename', 'setlocale', 'time', 'tmpname',
    },
    math = {
        'abs', 'acos', 'asin', 'atan', 'atan2', 'ceil', 'cos', 'cosh', 'deg',
        'exp', 'floor', 'fmod', 'frexp', 'huge', 'ldexp', 'log', 'log10',
        'max', 'min', 'modf', 'pi', 'pow', 'rad', 'random', 'randomseed',
        'sin', 'sinh', 'sqrt', 'tan', 'tanh',
    },
}

local LUA_KEYWORDS = {
    ['and'] = true, ['break'] = true, ['do'] = true, ['else'] = true,
    ['elseif'] = true, ['end'] = true, ['false'] = true, ['for'] = true,
    ['function'] = true, ['goto'] = true, ['if'] = true, ['in'] = true,
    ['local'] = true, ['nil'] = true, ['not'] = true, ['or'] = true,
    ['repeat'] = true, ['return'] = true, ['then'] = true, ['true'] = true,
    ['until'] = true, ['while'] = true,
}

--- Пригодно ли имя для записи через точку: box.foo, а не box["foo-bar"].
local function is_plain_name(name)
    return name:match('^[%a_][%w_]*$') ~= nil and not LUA_KEYWORDS[name]
end

-- Служебные поля, которые незачем показывать в автодополнении.
local function is_private(key)
    return type(key) ~= 'string'
        or key:sub(1, 1) == '_'
        or key == 'internal'
        or key:find('^internal%.')
end

local function sorted_keys(tbl)
    local keys = {}
    for key in pairs(tbl) do
        if not is_private(key) then
            keys[#keys + 1] = key
        end
    end
    table.sort(keys)
    return keys
end

--- Возвращает список имён параметров функции по отладочной информации.
--- Для C-функций имена недоступны, остаётся только вариадическая сигнатура.
local function describe_params(fn)
    local info = debug.getinfo(fn, 'u')
    if not info then
        return { '...' }
    end
    local params = {}
    for i = 1, (info.nparams or 0) do
        -- Для C-функций имена локальных переменных недоступны и getlocal
        -- возвращает nil — тогда параметр остаётся безымянным.
        local name = debug.getlocal(fn, i) or ('arg' .. i)
        params[#params + 1] = is_plain_name(name) and name or ('arg' .. i)
    end
    -- Точное число параметров известно только для Lua-функций; во всех
    -- остальных случаях допускаем любые аргументы, чтобы не плодить ложные
    -- предупреждения о лишних аргументах.
    if info.isvararg or #params == 0 then
        params[#params + 1] = '...'
    end
    return params
end

--- Тип поля в терминах аннотаций: значения, которые нельзя описать точнее,
--- получают тип по их Lua-типу.
local function scalar_type(value)
    local kind = type(value)
    if kind == 'number' or kind == 'string' or kind == 'boolean' then
        return kind
    elseif kind == 'userdata' then
        return 'userdata'
    end
    -- cdata бывает и данными (box.NULL), и вызываемой функцией из ffi
    -- (clock.time), различить их без вызова нельзя — оставляем any,
    -- иначе анализатор запретит вызов.
    return 'any'
end

--- Приводит путь модуля к идентификатору Lua: 'http.client' -> 'http_client'.
local function to_identifier(path)
    return (path:gsub('[^%w]', '_'))
end

--- Собирает функции, описанные вручную в .types/tarantool/manual/.
--- Автогенерация их пропускает: иначе сигнатура без ---@return подмешивала бы
--- nil в тип результата и анализатор требовал бы проверок на nil.
---
--- Разбор построчный: `---@class X` задаёт текущий класс, следующее
--- объявление таблицы связывает с ним имя переменной, а `function var.name`
--- добавляет имя в список перекрытых.
local function collect_manual_overrides()
    local overrides = {}
    local fio = require('fio')
    if not fio.path.is_dir(MANUAL_DIR) then
        return overrides
    end

    for _, entry in ipairs(fio.listdir(MANUAL_DIR)) do
        if entry:match('%.lua$') then
            local fh = io.open(MANUAL_DIR .. '/' .. entry, 'r')
            if fh then
                local content = fh:read('*a')
                fh:close()

                local pending_class, vars = nil, {}
                for line in content:gmatch('[^\n]+') do
                    local class = line:match('^%s*%-%-%-@class%s+([%w_%.]+)')
                    if class then
                        pending_class = class
                    end

                    local var = line:match('^%s*local%s+([%w_]+)%s*=%s*{%s*}')
                        or line:match('^%s*([%w_]+)%s*=%s*{%s*}')
                    if var and pending_class then
                        vars[var] = pending_class
                        pending_class = nil
                    end

                    local owner, fn = line:match('^%s*function%s+([%w_]+)[%.:]([%w_]+)%s*%(')
                    local target = owner and vars[owner]
                    if target and fn then
                        overrides[target] = overrides[target] or {}
                        overrides[target][fn] = true
                    end
                end
            end
        end
    end
    return overrides
end

--- Собирает имена модулей, покрытых встроенными курированными аннотациями
--- (.types/tarantool/bundled). Такие модули из генерации исключаются:
--- интроспекция дала бы плоские сигнатуры без типов, и при слиянии классов
--- они засоряли бы курированные описания дублями с типом any.
---
--- Имя модуля восстанавливается из раскладки каталога: `fiber.lua` → fiber,
--- `net/box.lua` → net.box. Подкаталоги вида box/ описывают подсистемы
--- модуля верхнего уровня и отдельными именами не считаются, но добавление
--- box.schema в набор безвредно: в списке MODULES таких имён нет.
local function collect_bundled_modules()
    local bundled = {}
    local fio = require('fio')
    if not fio.path.is_dir(BUNDLED_LIBRARY_DIR) then
        return bundled
    end
    for _, entry in ipairs(fio.listdir(BUNDLED_LIBRARY_DIR)) do
        local top = entry:match('^(.+)%.lua$')
        if top then
            bundled[top] = true
        elseif fio.path.is_dir(BUNDLED_LIBRARY_DIR .. '/' .. entry) then
            for _, inner in ipairs(fio.listdir(BUNDLED_LIBRARY_DIR .. '/' .. entry)) do
                local name = inner:match('^(.+)%.lua$')
                if name then
                    bundled[entry .. '.' .. name] = true
                end
            end
        end
    end
    return bundled
end

--- Собирает объявления функций стандартных библиотек из файлов бандла.
--- Расширения Tarantool описаны не только в одноимённых файлах
--- (string.lua), но и, например, в tarantool.lua (table.deepcopy,
--- table.new) — пропуск по имени файла их не покрывает. Возвращает набор
--- вида {['table.deepcopy'] = true}.
local function collect_bundled_stdlib()
    local found = {}
    local fio = require('fio')
    if not fio.path.is_dir(BUNDLED_LIBRARY_DIR) then
        return found
    end
    local function scan_file(path)
        local fh = io.open(path, 'r')
        if not fh then
            return
        end
        local content = fh:read('*a')
        fh:close()
        for lib, name in content:gmatch('function%s+([%w_]+)%.([%w_]+)%s*%(') do
            if STDLIB[lib] then
                found[lib .. '.' .. name] = true
            end
        end
    end
    for _, entry in ipairs(fio.listdir(BUNDLED_LIBRARY_DIR)) do
        local full = BUNDLED_LIBRARY_DIR .. '/' .. entry
        if entry:match('%.lua$') then
            scan_file(full)
        elseif fio.path.is_dir(full) then
            for _, inner in ipairs(fio.listdir(full)) do
                if inner:match('%.lua$') then
                    scan_file(full .. '/' .. inner)
                end
            end
        end
    end
    return found
end

local Emitter = {}
Emitter.__index = Emitter

function Emitter.new(root_name, overrides)
    return setmetatable({
        root_name = root_name,
        overrides = overrides or {},  -- класс -> набор вручную описанных функций
        visited = {},                 -- таблица -> имя класса, защита от циклов
        classes = {},                 -- описания классов в порядке объявления
    }, Emitter)
end

--- Рекурсивно описывает таблицу как класс с полями и методами.
--- Возвращает имя класса, под которым таблица объявлена.
function Emitter:walk(tbl, class_name, depth)
    if self.visited[tbl] then
        return self.visited[tbl]
    end
    self.visited[tbl] = class_name

    local class = { name = class_name, fields = {}, functions = {}, links = {} }
    self.classes[#self.classes + 1] = class

    local manual = self.overrides[class_name] or {}
    for _, key in ipairs(sorted_keys(tbl)) do
        if not manual[key] then
            local ok, value = pcall(function() return tbl[key] end)
            if ok then
                local kind = type(value)
                if kind == 'function' then
                    class.functions[#class.functions + 1] = {
                        name = key,
                        params = describe_params(value),
                    }
                elseif kind == 'table' and depth < MAX_DEPTH then
                    local child = self:walk(value, class_name .. '.' .. key, depth + 1)
                    class.links[#class.links + 1] = { name = key, class = child }
                else
                    class.fields[#class.fields + 1] = {
                        name = key,
                        type = scalar_type(value),
                    }
                end
            end
        end
    end

    -- Таблицы-функторы (box.cfg{...}) вызываются напрямую через __call.
    local meta = getmetatable(tbl)
    if type(meta) == 'table' and type(meta.__call) == 'function' then
        class.callable = true
    end

    return class_name
end

--- Формирует объявление одного класса.
--- `var` — имя переменной таблицы, `is_local` — объявлять ли её локальной.
local function render_class(class, var, is_local, out)
    local function w(fmt, ...)
        out[#out + 1] = select('#', ...) > 0 and fmt:format(...) or fmt
    end

    w('---@class %s', class.name)
    for _, field in ipairs(class.fields) do
        local name = is_plain_name(field.name) and field.name
            or ('[%q]'):format(field.name)
        w('---@field %s %s', name, field.type)
    end
    for _, link in ipairs(class.links) do
        local name = is_plain_name(link.name) and link.name
            or ('[%q]'):format(link.name)
        w('---@field %s %s', name, link.class)
    end
    if class.callable then
        w('---@overload fun(...): any')
    end
    w('%s%s = {}', is_local and 'local ' or '', var)
    w('')

    for _, fn in ipairs(class.functions) do
        local params = table.concat(fn.params, ', ')
        -- Без объявленного результата анализатор считает, что функция
        -- возвращает nil, и ругается на любое использование результата.
        w('---@return any ...')
        if is_plain_name(fn.name) then
            w('function %s.%s(%s) end', var, fn.name, params)
        else
            w('%s[%q] = function(%s) end', var, fn.name, params)
        end
    end
    if #class.functions > 0 then
        w('')
    end
end

--- Собирает текст stub-файла модуля.
function Emitter:render(kind)
    local out = {}
    out[#out + 1] = kind == 'require'
        and ('---@meta %s'):format(self.root_name)
        or '---@meta'
    out[#out + 1] = ''
    out[#out + 1] = ('-- Сгенерировано tools/gen_stubs.lua из Tarantool %s.'):format(_TARANTOOL)
    out[#out + 1] = '-- Файл перезаписывается целиком: правки вносите в .types/tarantool/manual/.'
    out[#out + 1] = ''

    local root_var = to_identifier(self.root_name)
    for index, class in ipairs(self.classes) do
        -- Корневой класс глобального модуля объявляется без local, иначе
        -- анализатор посчитает глобальную переменную неопределённой.
        local is_root = index == 1
        local is_global_root = is_root and kind == 'global'
        local var = is_global_root and self.root_name
            or (is_root and root_var or to_identifier(class.name))
        render_class(class, var, not is_global_root, out)
    end

    if kind == 'require' then
        out[#out + 1] = ('return %s'):format(root_var)
        out[#out + 1] = ''
    end

    return table.concat(out, '\n')
end

--- Расширения стандартной библиотеки описываются дописыванием функций
--- прямо в глобальные таблицы: объявлять для них класс нельзя — это
--- перекрыло бы встроенные определения анализатора.
local function render_stdlib_extensions(bundled, bundled_stdlib)
    local out = {
        '---@meta',
        '',
        ('-- Сгенерировано tools/gen_stubs.lua из Tarantool %s.'):format(_TARANTOOL),
        '-- Расширения стандартной библиотеки Lua, добавленные Tarantool.',
        '',
    }
    for _, lib_name in ipairs({ 'string', 'table', 'os', 'math' }) do
        -- Расширения библиотек, покрытых бандлом (string.lua описывает
        -- и добавки Tarantool), пропускаются — иначе дубли в completion.
        if not bundled[lib_name] then
            local known = {}
            for _, name in ipairs(STDLIB[lib_name]) do
                known[name] = true
            end
            local added = {}
            for _, key in ipairs(sorted_keys(_G[lib_name])) do
                if not known[key] and not bundled_stdlib[lib_name .. '.' .. key]
                    and type(_G[lib_name][key]) == 'function' and is_plain_name(key) then
                    added[#added + 1] = ('---@return any ...\nfunction %s.%s(%s) end')
                        :format(lib_name, key, table.concat(describe_params(_G[lib_name][key]), ', '))
                end
            end
            if #added > 0 then
                out[#out + 1] = ('-- %s'):format(lib_name)
                for _, line in ipairs(added) do
                    out[#out + 1] = line
                end
                out[#out + 1] = ''
            end
        end
    end
    return table.concat(out, '\n')
end

local function write_file(path, content)
    local fh = assert(io.open(path, 'w'))
    fh:write(content)
    fh:close()
end

local function main()
    -- box.schema, box.space и прочие подсистемы появляются только после
    -- инициализации, поэтому конфигурируем инстанс во временном каталоге.
    local sandbox = os.tmpname() .. '.gen'
    os.execute('mkdir -p ' .. sandbox)
    box.cfg {
        memtx_dir = sandbox,
        wal_dir = sandbox,
        vinyl_dir = sandbox,
        log = sandbox .. '/gen.log',
        log_level = 0,
    }

    os.execute('rm -rf ' .. OUT_DIR)
    os.execute('mkdir -p ' .. OUT_DIR)

    local overrides = collect_manual_overrides()
    local bundled = collect_bundled_modules()

    local generated, covered = 0, 0
    for _, module in ipairs(MODULES) do
        if bundled[module.name] then
            covered = covered + 1
        else
            local ok, value = pcall(require, module.name)
            if ok and type(value) == 'table' then
                local emitter = Emitter.new(module.name, overrides)
                emitter:walk(value, module.name, 1)
                local file = OUT_DIR .. '/' .. to_identifier(module.name) .. '.lua'
                write_file(file, emitter:render(module.kind))
                generated = generated + 1
            else
                io.stderr:write(('пропущен модуль %s\n'):format(module.name))
            end
        end
    end

    write_file(OUT_DIR .. '/stdlib_ext.lua', render_stdlib_extensions(bundled, collect_bundled_stdlib()))

    -- Маркер версии интерпретатора: стартовая диагностика сверяет его
    -- с установленным tarantool и предлагает перегенерацию при смене версии.
    write_file(OUT_DIR .. '/.tarantool-version', _TARANTOOL .. '\n')

    os.execute('rm -rf ' .. sandbox)
    print(('сгенерировано модулей: %d, покрыто встроенными аннотациями: %d -> %s')
        :format(generated, covered, OUT_DIR))
    os.exit(0)
end

main()
