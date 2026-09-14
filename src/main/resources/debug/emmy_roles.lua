--[[
Действующие роли инстанса по кластерной конфигурации.

Плагин подключает отладчик к кластеру ролью, поставленной первой в списке
(переменная TT_ROLES перекрывает список ролей инстанса), и обязан
перечислить в ней и остальные роли — иначе они не применятся. Разбирать
config.yaml в IDE ненадёжно: roles задаются на любом уровне (группа,
репликасет, инстанс), и побеждает самый близкий к инстансу. Поэтому список
считает сам Tarantool тем же модулем, каким ядро собирает конфигурацию
инстанса.

Запуск: tt run emmy_roles.lua <config.yaml> <имя инстанса>
Печатает одну строку — JSON-массив ролей (пустой, если ролей нет).
]]

local json = require('json')
local yaml = require('yaml')

local config_path, instance = arg[1], arg[2]

if config_path == nil or instance == nil then
    io.stderr:write('нужны два довода: путь к config.yaml и имя инстанса\n')
    os.exit(2)
end

local file, open_error = io.open(config_path, 'r')

if file == nil then
    io.stderr:write(('config.yaml не открыт: %s\n'):format(tostring(open_error)))
    os.exit(1)
end

local text = file:read('*a')
file:close()

-- Внутренний модуль ядра: тот же, которым конфигурация собирается для
-- инстанса при подъёме. Подстановки {{ context.* }} остаются как есть —
-- ролям они не нужны.
local cluster_config = require('internal.config.cluster_config')
local ok, instantiated = pcall(function()
    local cluster = yaml.decode(text)

    -- Неизвестный инстанс — отказ, а не пустой список: пустой список
    -- плагин принял бы за «ролей нет» и снял бы настоящие роли.
    if cluster_config:find_instance(cluster, instance) == nil then
        error(('инстанса %s в конфигурации нет'):format(instance), 0)
    end

    return cluster_config:instantiate(cluster, instance)
end)

if not ok then
    io.stderr:write(('конфигурация инстанса %s не собрана: %s\n'):format(instance, tostring(instantiated)))
    os.exit(1)
end

local roles = instantiated.roles or {}

-- Пустой массив кодируется как {} — читающей стороне нужны скобки.
print(#roles == 0 and '[]' or json.encode(roles))
os.exit(0)
