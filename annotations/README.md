# Курированные EmmyLua-аннотации Tarantool

Каталог `tarantool/` — копия набора tarantool-annotations из официального
VS Code-расширения [tarantool/tarantool-vscode](https://github.com/tarantool/tarantool-vscode)
(коммит `137696b385a53b2206380519c944c8aa42e7db79`, релиз 0.3.0,
лицензия BSD-2-Clause — файл `tarantool/LICENSE` обязан ехать вместе
с аннотациями).

Состав:

* `tarantool/Library/` — аннотации встроенных модулей (box с подсистемами,
  fiber, net.box, fio, datetime и другие) с ручными уточнениями сигнатур,
  перегрузками и документацией;
* `tarantool/Rocks/` — аннотации популярных роков; сейчас это vshard
  (router, storage, replicaset, cfg).

При сборке каталог упаковывается в `stubs/tarantool-annotations.zip`
(задача `annotationsZip` в build.gradle.kts) и разворачивается плагином
в `.types/tarantool/bundled/` проекта — см. `BundledAnnotations.kt`.

## Локальные правки поверх 137696b

Каталог больше не байт-в-байт копия: после сверки с API живого
Tarantool 3.8 (эталон — дамп `pairs()` установленного инстанса)
дописаны недостающие объявления и исправлены ошибки. Основное:

* починены непарсящиеся файлы (http/client.lua, buffer.lua,
  tarantool.lua) и опечатки (`xlog.paris`, статус `suspected`);
* insert/replace/put получили `| tuple_type[]` в union параметра —
  без него emmylua_ls давал ложный missing-fields на табличных
  литералах (методы класса он считает обязательными членами,
  EmmyLuaLs/emmylua-analyzer-rust#837 — by design);
* добавлены отсутствовавшие: datetime.now/parse_date/TZ, fio.path,
  модульные функции fiber, box.func/lib/prepare/sequence/runtime,
  операции net.box-спейсов, socket.from_fd/socketpair, модуль
  `tarantool`, новые файлы digest.lua и msgpack.lua; в
  tuple_type_name добавлены 'datetime' и 'interval'.

Правки отправлены апстрим; до их вливания обновление через `rm -rf`
недопустимо — сначала сверить дифф с upstream HEAD.

## Обновление

```sh
git clone --depth 1 https://github.com/tarantool/tarantool-vscode
rm -rf annotations/tarantool/Library annotations/tarantool/Rocks
cp -R tarantool-vscode/tarantool-annotations/{Library,Rocks,LICENSE} annotations/tarantool/
```

После обновления зафиксируйте новый коммит источника в этом файле
и перенесите локальные правки, не влитые апстримом.
Пользовательские проекты получат свежий бандл автоматически: маркер
`.bundle-version` в развёрнутом каталоге сверяется с версией плагина.
