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

## Обновление

```sh
git clone --depth 1 https://github.com/tarantool/tarantool-vscode
rm -rf annotations/tarantool/Library annotations/tarantool/Rocks
cp -R tarantool-vscode/tarantool-annotations/{Library,Rocks,LICENSE} annotations/tarantool/
```

После обновления зафиксируйте новый коммит источника в этом файле.
Пользовательские проекты получат свежий бандл автоматически: маркер
`.bundle-version` в развёрнутом каталоге сверяется с версией плагина.
