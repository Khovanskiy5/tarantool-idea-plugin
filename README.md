# Tarantool — плагин для IntelliJ IDEA

[![Build](https://github.com/Khovanskiy5/tarantool-idea-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/Khovanskiy5/tarantool-idea-plugin/actions/workflows/build.yml)

Неофициальный плагин, добавляющий в IntelliJ IDEA полный цикл разработки
под [Tarantool](https://www.tarantool.io/): запуск и отладку Lua-кода,
управление инстансами (локально, в Docker или в Kubernetes), просмотр
и редактирование данных, тесты, схемы конфигурации и генерацию типов.

Документация: [руководство пользователя](docs/user-guide.md) ·
[руководство разработчика](docs/development.md).

## Возможности

### Запуск и отладка

* **Конфигурация «Tarantool»** — скрипт, интерпретатор, аргументы,
  рабочий каталог, переменные окружения; в полях работают макросы IDE
  (`$PROJECT_DIR$`, `$FilePath$` и другие).
* **Запуск открытого файла** — правый клик по `.lua`-файлу → *Run*
  (`⌃⇧R` / `Ctrl+Shift+F10`), конфигурация создаётся автоматически.
* **LUA_PATH** — корень проекта и `src/` добавляются в пути поиска
  модулей: `require('model.users')` работает без обёрток.
* **Кликабельные трейсбеки** — ссылки `file.lua:line` в выводе процесса;
  имена без каталога (как их печатает логгер Tarantool) разрешаются
  поиском по индексу проекта.
* **Встроенный отладчик** — флажок «tarantool -d»: скрипт стартует под
  интерактивной консолью luadebug прямо в окне Run.
* **Emmy-отладчик** — Tools → Настроить Emmy-отладчик Tarantool: плагин
  кладёт в проект хелпер `emmy_debug.lua` и конфигурацию подключения;
  точки останова и шаги — через плагин EmmyLua2, в том числе
  в инстансах кластера (`TARANTOOL_DEBUG=<имя инстанса> tt start`).

### Инстансы

* **Панель «Tarantool»** — таблица состояния инстансов
  с автообновлением, кнопки запуска/остановки/перезапуска, живые логи
  и интерактивная консоль выбранного инстанса во встроенном Терминале.
* **Конфигурация «tt (Tarantool CLI)»** — любая команда tt в окне Run
  с псевдотерминалом: start, stop, status, build, log -f и другие.
* **Режимы запуска** (настройка проекта): локально; в Docker — tt
  исполняется внутри контейнера через префикс вроде
  `docker compose exec tarantool`; в Kubernetes — панель показывает
  поды (`kubectl get pods`), перезапускает их, читает логи
  (`kubectl logs -f`) и открывает консоль (`kubectl exec -it`).

### Данные (IDEA Ultimate)

* **Автонастройка Database Tools** — при открытии tt-проекта плагин
  сам регистрирует драйвер и создаёт источники данных по `config.yaml`:
  роутеры и лидеры репликасетов, учётные данные из `credentials`.
* **Драйвер-обёртка с эмуляцией автокоммита** — редактирование данных
  из грида работает, несмотря на отсутствие интерактивных транзакций
  в JDBC-драйвере Tarantool.
* **SQL-диалект Tarantool** — SQL-92 плюс `SEQSCAN`, типы данных
  Tarantool и `SET SESSION`; без ложных ошибок подсветки.

### Тесты, проект, конфигурация

* **Раннер luatest** — дерево результатов из TAP-вывода; правый клик
  по `*_test.lua` / `test_*.lua` → Run.
* **Мастер нового проекта** — New Project → Generators → Tarantool:
  шаблоны tt create (single_instance, cluster, vshard_cluster,
  config_storage); в открытом проекте — контекстное меню → New →
  **Приложение Tarantool из шаблона** (при отсутствии окружения
  сначала `tt init`).
* **Схемы конфигурации** — автодополнение и валидация `config.yaml`,
  `cluster.yml` и `source.yml` (официальная JSON Schema кластерной
  конфигурации Tarantool 3) и `tt.yaml`; для `config.yaml` вне
  tt-окружения — баннер «включить схему?».
* **Типы для EmmyLua2** — встроенные курированные аннотации
  (все модули плюс рок vshard; разворачиваются автоматически при
  открытии tt-проекта, tarantool не нужен) и генерация интроспекцией
  для непокрытых модулей — автодополнение по `box`, `fiber`, `net.box`,
  `vshard`.
* **Диагностика окружения** — при открытии tt-проекта: EmmyLua2, пути
  к tarantool/tt, устаревшие типы; одна сводка с кнопками-исправлениями.
* **Просмотр `.snap`/`.xlog`/`.vylog`** — YAML-расшифровка через
  `tt cat` в редакторе только для чтения.
* **Проверка синтаксиса** — `tt check` из контекстного меню файла.
* **Live-шаблоны** — `tspace`, `tonce`, `tfiber`, `tatomic`, `tnetbox`,
  `tlog`, `twatch`.

## Требования

| Что | Версия |
|---|---|
| IntelliJ IDEA (Community или Ultimate) | 2026.1+ |
| Tarantool | 3.x |
| tt CLI | 2.x |
| EmmyLua2 (опционально, язык Lua) | 0.24+ |

## Установка

Скачайте zip со [страницы релизов](https://github.com/Khovanskiy5/tarantool-idea-plugin/releases)
или соберите сами, затем: **Settings → Plugins → ⚙ → Install Plugin from
Disk…** → выберите `tarantool-idea-plugin-<версия>.zip` → перезапустите IDE.

## Сборка из исходников

```sh
./gradlew buildPlugin        # дистрибутив в build/distributions/*.zip
./gradlew test               # юнит-тесты
./gradlew verifyPlugin       # Plugin Verifier
./gradlew runIde             # песочница IDE с плагином
```

Требуется JDK 21. Локальная сборка идёт против установленной IDE
(`/Applications/IntelliJ IDEA.app`); без неё — против скачиваемого
дистрибутива: `./gradlew -PuseLocalIde=false …` (так работает CI).
Подробности — в [руководстве разработчика](docs/development.md).
