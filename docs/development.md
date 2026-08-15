# Руководство разработчика

Как устроен плагин, как его собирать, проверять и расширять.

## Сборка

```sh
./gradlew buildPlugin      # дистрибутив → build/distributions/*.zip
./gradlew test             # юнит-тесты (чистая логика, без платформы)
./gradlew verifyPlugin     # Plugin Verifier: совместимость и устаревшие API
./gradlew runIde           # песочница IDE с установленным плагином
```

Требуется JDK 21. Локальная сборка идёт против установленной IDE
(`/Applications/IntelliJ IDEA.app`) — без загрузки дистрибутива платформы.
Когда локальной IDE нет (CI), скачивается IntelliJ IDEA Community:

```sh
./gradlew -PuseLocalIde=false test buildPlugin verifyPlugin
```

CI настроен в `.github/workflows/build.yml`: сборка, тесты, верификация
и выгрузка zip-артефакта на каждый push/PR.

## Технологии и соглашения

* **IntelliJ Platform Gradle Plugin 2.x** — современная система сборки
  плагинов; конфигурация в `build.gradle.kts`, блок `intellijPlatform`.
* **Kotlin, JDK 21**; `kotlin.stdlib.default.dependency=false` — stdlib
  предоставляет платформа, класть свою в дистрибутив нельзя.
* Персистентность конфигураций запуска — Options-классы (`BaseState`
  с делегатами `string()`, `property()`, `map()`), не ручной XML.
* UI — Kotlin UI DSL v2 (`com.intellij.ui.dsl.builder.panel`).
* Строки интерфейса — ресурс-бандл `messages/TarantoolBundle.properties`
  (UTF-8), доступ через `TarantoolBundle.message(...)`.
* Идентификаторы в коде английские, комментарии и пользовательские
  строки — русские.
* `ActionUpdateThread.BGT` у всех действий; `DumbAware`, где индексы
  не нужны; долгие операции — `Task.Backgroundable`/`Task.Modal`,
  внешние процессы — `GeneralCommandLine` + `CapturingProcessHandler`.

## Карта кода

```
src/main/kotlin/com/khovanskiy/tarantool/
├── TarantoolBundle.kt        доступ к ресурс-бандлу
├── TarantoolIcons.kt         иконки
├── run/                      конфигурация запуска «Tarantool»
│   ├── TarantoolConfigurationType.kt      тип+фабрика (SimpleConfigurationType)
│   ├── TarantoolRunConfiguration.kt       конфигурация и её Options-класс
│   ├── TarantoolCommandLineState.kt       сборка командной строки, LUA_PATH, -d
│   ├── TarantoolSettingsEditor.kt         форма настроек
│   ├── TarantoolRunConfigurationProducer.kt  создание из контекста .lua-файла
│   ├── TarantoolTracebackFilter.kt        гиперссылки file.lua:line в консоли
│   ├── TarantoolInterpreter.kt            поиск бинарника tarantool
│   └── LuaPaths.kt                        составление LUA_PATH (покрыто тестами)
├── tt/                       конфигурация запуска «tt (Tarantool CLI)»
│   ├── TtConfigurationType.kt
│   ├── TtRunConfiguration.kt
│   ├── TtCommandLineState.kt              PTY; интерактивный connect — не сюда, а в Терминал
│   ├── TtSettingsEditor.kt                автодополнение команд
│   ├── CheckSyntaxAction.kt               «Проверить синтаксис (tt check)»
│   ├── TtCli.kt                           поиск бинарника tt
│   └── TtExecution.kt                     построение команд tt/kubectl с учётом режима
├── luatest/                  раннер тестов luatest
│   ├── LuatestRunConfiguration.kt         тип+конфигурация+Options
│   ├── LuatestCommandLineState.kt         SM-консоль (дерево тестов)
│   ├── LuatestOutputConverter.kt          TAP → события SMTestRunner
│   ├── LuatestTapParser.kt                чистый разбор TAP (юнит-тесты)
│   ├── LuatestSettingsEditor.kt
│   └── LuatestRunConfigurationProducer.kt *_test.lua / test_*.lua
├── toolwindow/               панель «Tarantool»
│   ├── TarantoolToolWindowFactory.java    фабрика (Java — мосты Kotlin
│   │                                      к default-методам путают верификатор)
│   ├── InstancesPanel.kt                  таблица инстансов/подов + действия
│   ├── TtStatus.kt                        разбор tt status -f json
│   ├── KubeStatus.kt                      разбор kubectl get pods -o json (юнит-тесты)
│   └── TarantoolDataKeys.kt               выбранный инстанс для действий модулей
├── terminal/                 модуль интеграции с Терминалом
│   └── OpenInstanceConsoleAction.kt       tt connect во вкладке Терминала
├── lua/                      модуль интеграции с EmmyLua2
│   └── TarantoolLuaRunLineMarkerContributor.kt  гуттер ▶ на .lua
├── schema/                   JSON-схемы для YAML
│   ├── TarantoolConfigSchemaProviderFactory.kt   config.yaml/cluster.yml/source.yml + tt.yaml
│   ├── TarantoolConfigHeuristics.kt      «похоже на кластерную конфигурацию» (юнит-тесты)
│   └── TarantoolConfigNotificationProvider.kt    баннер «включить схему?» для config.yaml
├── wal/                      файлы данных
│   ├── TarantoolWalFileType.kt            тип .snap/.xlog/.vylog
│   ├── WalFileEditorProvider.kt           регистрация редактора
│   └── WalPreviewFileEditor.kt            read-only viewer поверх tt cat
├── project/                  мастер нового проекта и приложения
│   ├── TarantoolNewProjectWizard.kt       пункт в IDEA (newProjectWizard.generator)
│   ├── TarantoolProjectGenerator.kt       пункт в PyCharm/WebStorm (directoryProjectGenerator)
│   ├── NewTtApplicationAction.kt          «Приложение из шаблона…» в меню New открытого проекта
│   └── TtScaffolder.kt                    общий запуск tt init/tt create (новый проект и createApp)
├── templates/
│   └── TarantoolLuaTemplateContext.kt     контекст live-шаблонов (.lua)
├── debugger/
│   ├── SetupEmmyDebuggerAction.kt         раскладка emmy_debug.lua и конфигурации attach (ручной сценарий)
│   ├── EmmyCore.kt                        путь к нативному агенту внутри плагина EmmyLua2
│   ├── DebugLaunch.kt                     сеанс отладки: порт, загрузчик, маркеры рукопожатия (юнит-тесты)
│   ├── EmmySession.kt                     запуск сессии EmmyLua2 (рефлексия, плагин необязателен)
│   ├── DebugAttach.kt                     ожидание порта в фоне и подключение сессии
│   ├── TarantoolDebugRunner.kt            programRunner: кнопка Debug у конфигурации «Tarantool»
│   └── ClusterDebug.kt                    кнопка «Запустить с отладчиком» на панели инстансов
├── settings/
│   ├── TarantoolSettings.kt               PersistentStateComponent (пути)
│   ├── TarantoolSettingsConfigurable.kt   страница Settings → Tools → Tarantool
│   ├── TarantoolProjectSettings.kt        режим запуска + ответы на баннер схемы (.idea/tarantool.xml)
│   └── TarantoolProjectConfigurable.kt    подстраница «Режим запуска»
├── health/
│   ├── TarantoolHealthCheck.kt            стартовая диагностика + авторазворачивание бандла
│   └── VersionNumbers.kt                  числовое сравнение версий (юнит-тесты)
└── stubs/
    ├── GenerateTarantoolStubsAction.kt    установка типов: бандл + генерация интроспекцией
    ├── BundledAnnotations.kt              разворачивание zip курированных аннотаций (юнит-тесты)
    ├── Emmyrc.kt                          создание/дописывание .emmyrc.json (юнит-тесты)
    └── ManualTypesMigration.kt            миграция легаси-имён ручных типов

annotations/tarantool/             курированные аннотации (копия из tarantool-vscode,
                                   BSD-2; обновление — см. annotations/README.md);
                                   сборка пакует их в stubs/tarantool-annotations.zip

src/main/resources/
├── META-INF/plugin.xml            манифест, регистрация расширений
├── com.khovanskiy.tarantool.*.xml дескрипторы content-модулей (json, sql, lua, terminal)
├── schemas/tarantool-config.json  схема config.yaml (из config:jsonschema())
├── schemas/tt-config.json         схема tt.yaml (по исходникам tt)
├── liveTemplates/Tarantool.xml    live-шаблоны
├── stubs/gen_stubs.lua            генератор типов (запускается tarantool'ом)
├── stubs/manual/                  бывшие ручные стабы: больше не раскладываются,
│                                  нужны health check'у как эталон для «нетронутых копий»
├── debug/                         emmy_debug.lua и конфигурация attach debugger
└── messages/TarantoolBundle.properties
```

## Использованные точки расширения

| EP | Класс | Зачем |
|---|---|---|
| `configurationType` ×3 | `TarantoolConfigurationType`, `TtConfigurationType`, `LuatestConfigurationType` | конфигурации запуска |
| `runConfigurationProducer` ×2 | `TarantoolRunConfigurationProducer`, `LuatestRunConfigurationProducer` | Run для .lua и тестов из контекста |
| `fileType` | `TarantoolWalFileType` | .snap/.xlog/.vylog как бинарные |
| `fileEditorProvider` | `WalFileEditorProvider` | просмотрщик через tt cat |
| `newProjectWizard.generator` | `TarantoolNewProjectWizard` | пункт в мастере IDEA |
| `directoryProjectGenerator` | `TarantoolProjectGenerator` | пункт в PyCharm/WebStorm |
| `liveTemplateContext` + `defaultLiveTemplates` | `TarantoolLuaTemplateContext` | шаблоны в .lua |
| `applicationConfigurable` | `TarantoolSettingsConfigurable` | страница настроек (пути) |
| `projectConfigurable` | `TarantoolProjectConfigurable` | режим запуска проекта |
| `toolWindow` | `TarantoolToolWindowFactory` | панель инстансов |
| `postStartupActivity` ×2 | `TarantoolHealthCheck`, `TarantoolDataSourceStartup`** | диагностика окружения и разворачивание бандла типов; автонастройка драйвера и источников данных |
| `notificationGroup` | — | всплывающие уведомления |
| `runLineMarkerContributor`*** | `TarantoolLuaRunLineMarkerContributor` | гуттер ▶ на .lua |
| `JavaScript.JsonSchema.ProviderFactory`* | `TarantoolConfigSchemaProviderFactory` | схемы YAML |
| `editorNotificationProvider`* | `TarantoolConfigNotificationProvider` | баннер «включить схему?» над config.yaml |
| `com.intellij.database.dbms`** | `TarantoolDbms` | СУБД Tarantool в реестре Database |
| `com.intellij.sql.dialect`** | `TarantoolSqlDialect` | SQL-диалект Tarantool |

\* регистрируется в content-модуле Plugin Model v2
(`com.khovanskiy.tarantool.json.xml` в корне ресурсов) — он грузится
только при наличии JSON-плагина; историческое имя EP сохранено
платформой. Главный дескриптор объявляет модули в `<content>`, каждый
несёт атрибут `package`: у основного `com.khovanskiy.tarantool`,
у JSON-модуля — `com.khovanskiy.tarantool.schema`, у SQL-модуля —
`com.khovanskiy.tarantool.sql` (классы разводятся по префиксу пакета).

\** регистрируется в content-модуле `com.khovanskiy.tarantool.sql.xml`,
который зависит от модулей `intellij.database.dialects.base`
и `intellij.database.dialects.sql92` Database-плагина и грузится только
в IDE с Database-инструментами. Устройство диалекта:

* `TarantoolSqlDialect` наследует `SqlLanguageDialectBase`; `Dbms`
  создаётся публичной фабрикой `Dbms.create`;
* токены — конвенция `TokenClasses`: по имени `TarantoolTokens`
  инфраструктура находит в том же пакете `TarantoolReservedKeywords`
  (резерв SQL-92) и `TarantoolOptionalKeywords` (слова Tarantool —
  SEQSCAN, типы данных — контекстные, парсер принимает их и как
  идентификаторы); Java-интерфейсы, потому что слова собираются
  рефлексией по публичным статическим полям;
* свой PSI-стек: `TarantoolParserDefinition`
  (`lang.parserDefinition language="TarantoolSQL"`) с собственным
  файловым типом узла и фабрикой элементов; лексер и парсер
  переиспользуются от SQL-92 (`Sql92Lexer`/`Sql92Parser` открыты);
* `shallResolve` подавляет ложный «unresolved table» для SEQSCAN
  в позиции таблицы.

Классы Database-плагина не имеют официальной документации — сигнатуры
добывались из байткода установленной IDE и проверяются компиляцией
и Plugin Verifier. Известный принятый риск: модуль
`intellij.database.dialects.sql92` помечен internal (namespace
jetbrains) — verifier считает зависимость на него compatibility
problem, поэтому verifyPlugin валит сборку только на структурных
дефектах (см. failureLevel в build.gradle.kts). Замены sql92-парсеру
нет; если будущая версия платформы реально закроет модуль, диалект
придётся пересматривать. Полная грамматика Tarantool (SEQSCAN как узел
грамматики, LIMIT/OFFSET) потребовала бы собственного generated-парсера —
grammar-kit исходников у SQL-плагина нет.

**Границы content-модулей — в одну сторону.** У каждого модуля (json,
sql, lua, terminal) свой загрузчик классов: модуль видит код ядра, ядро
код модуля — нет. Обращение из основного модуля роняет платформу
в рантайме («must not be requested from main classloader»), а компилятор
его пропускает — исходники у модулей общие. Поэтому всё, что нужно
и ядру, и модулю, живёт в основном модуле: так разбор `config.yaml`
переехал из `sql` в `cluster`, когда его понадобилось читать отладчику
(заодно отладка кластера перестала зависеть от Database-инструментов,
то есть от Ultimate). Направление зависимостей проверяет
`ModuleBoundariesTest` по исходникам.

\*** модуль `com.khovanskiy.tarantool.lua` зависит от плагина EmmyLua2
(`com.cppcxy.Intellij-EmmyLua`) — язык Lua регистрирует он; модуль
`com.khovanskiy.tarantool.terminal` зависит от плагина Terminal и
добавляет кнопку консоли в группу `Tarantool.InstancesExtraActions`
(панель отдаёт выбранный инстанс через `TarantoolDataKeys`). Важные
рантайм-грабли, добытые в бою: расширение `sql.dialect` берёт инстанс
только из статического поля `INSTANCE`; менеджер сериализации стабов
читает `getFileNodeType()` до регистрации языка (тип файла обязан быть
статическим); интерактивным консолям (`tt connect`, go-prompt) нужен
`/dev/tty`, которого нет у процессов Run-консоли.

Все использованные EP динамические: плагин ставится и обновляется
без перезапуска IDE, кроме случаев, когда платформа решает иначе.

## Архитектурные решения

**Два пункта мастера проектов.** В IDEA раздел Generators строится из
`newProjectWizard.generator`; `directoryProjectGenerator` там не виден —
он для IDE поменьше. Общая логика вынесена в `TtScaffolder`.

**Отладка без своего протокола, но с полной автоматикой.** Своего
XDebugger-протокола плагин не реализует: точки останова, стек и панель
переменных для Lua даёт EmmyLua2 (он же регистрирует сам язык, без него
отлаживать нечего). Плагин берёт на себя всё остальное — то, что раньше
пользователь делал руками:

* агент `emmy_core` ищется внутри установленного EmmyLua2 (`EmmyCore`),
  а не перебором каталогов JetBrains;
* Lua-файлы (`emmy_debug.lua`, `emmy_bootstrap.lua`) распаковываются
  в системный каталог IDE, поэтому в проекте их держать не нужно;
* агент подставляется в процесс без правки кода: скрипту — чанком `-e`
  (он выполняется до самого скрипта), кластеру — подменой `app.file`
  на загрузчик через переменную `TT_APP_FILE`, а настоящее приложение
  загрузчик выполняет сам;
* момент подключения синхронизируется двумя файлами-маркерами:
  процесс сообщает «порт открыт», IDE подтверждает «подключилась».
  Пробовать порт TCP-подключением нельзя — агент примет пробу за IDE
  и начнёт сессию. Пока нет подтверждения, загрузчик придерживает
  запуск приложения, поэтому точки останова работают и в стартовом
  коде; если IDE не подключилась за таймаут, приложение стартует
  всё равно — отладка не должна ломать запуск;
* сессия EmmyLua2 создаётся через рефлексию (`EmmySession`): временная
  конфигурация его типа `lua.emmy.debugger` с режимом TCP_CLIENT.
  Компилироваться и работать без EmmyLua2 плагин обязан, поэтому
  прямой зависимости на его классы нет.

Обратный режим транспорта (`emmy_core.tcpConnect`, «Debugger connect
IDE») не используется: с Tarantool он роняет процесс LuajitError.

cdata (тапл, decimal, uuid, datetime, interval) агент показывает пустым
значением, и починить это из Lua нельзя — хелпер `emmyHelper` вызывается
только для таблиц. Поэтому в процесс ставится глобальная `D()`,
разворачивающая такие значения в обычные таблицы для панели Watches.

**Без DataGrip-интеграции.** SQL-консоль сделана через
`tt connect -l sql`: API Database-инструментов закрытый и есть только
в Ultimate — зависимость, на которой умер официальный плагин Tarantool.

**Схемы как ресурсы.** Схема config.yaml генерируется самим Tarantool
(`require('config'):jsonschema()`) и кладётся в ресурсы как есть; схема
tt.yaml написана вручную по структуре `CliOpts` (cli/config/config.go).
Обновление при выходе новых версий: перегенерировать/сверить и заменить
файл в `schemas/`.

**Типы для EmmyLua2 — бандл плюс генерация.** База — курированные
аннотации из tarantool-vscode (annotations/tarantool, включая vshard):
богатые сигнатуры с документацией, работают без установленного
tarantool и разворачиваются в `.types/tarantool/bundled` автоматически.
Генерация интроспекцией дополняет бандл: gen_stubs.lua пропускает
покрытые им модули (список восстанавливается из раскладки каталога
Library) и снимает остальные точно под установленную версию сервера.
Ручные дефолты (stubs/manual) больше не раскладываются — их классы
сливались бы с курированными и дублировали completion; каталог manual
остался местом для правок пользователя, они имеют приоритет над
генерацией. .emmyrc.json создаётся только при отсутствии; существующий
файл принадлежит проекту и дописывается только кнопкой в уведомлении.

**Режимы запуска — префикс, а не свой транспорт.** Docker-режим — это
тот же tt, исполненный через префикс (`docker compose exec <сервис>`),
поэтому весь tt-функционал работает без изменений; точка сборки команд
одна — `TtExecution`. Kubernetes принципиально другой: жизненным циклом
управляет контроллер, поэтому панель переключается на kubectl-бекенд
(get pods / logs / delete pod / exec) вместо эмуляции tt поверх подов.
Протокольная часть (Database Tools, JDBC-шим) от режима не зависит —
ей нужен только доступный хост:порт (проброс портов, port-forward).

## Как добавить…

**…новую команду в live-шаблоны** — `resources/liveTemplates/Tarantool.xml`,
формат стандартный (`<template>`, переменные `$VAR$`, `$END$`), контекст
`TARANTOOL_LUA`.

**…поле в конфигурацию запуска** — свойство в Options-класс
(`TarantoolRunConfigurationOptions`), аксессор в конфигурацию, элемент
формы в `TarantoolSettingsEditor` (reset/apply/createEditor), использование
в `TarantoolCommandLineState`.

**…новый модуль в генератор типов** — список `MODULES`
в `resources/stubs/gen_stubs.lua`; модуль генерируется, только если
не покрыт бандлом (файл с его именем в bundled/Library).

**…свежий бандл аннотаций** — процедура в `annotations/README.md`:
скопировать Library/Rocks/LICENSE из tarantool-vscode и зафиксировать
коммит источника; пользователи получат обновление автоматически
по маркеру `.bundle-version` (сверяется с версией плагина).

**…проверку в стартовую диагностику** — список пунктов собирается
в `TarantoolHealthCheck.execute`: добавить `Item` с текстом и
fix-действием; сводка показывается только при непустом списке.

## Релиз

1. Поднять `version` в `build.gradle.kts`, дописать `<change-notes>`
   в plugin.xml.
2. `./gradlew clean test buildPlugin verifyPlugin` — верификатор должен
   ответить «Compatible» без списка устаревших API.
3. Тег `v<версия>`, zip из `build/distributions` — артефакт релиза.
4. Для Marketplace: заполнить страницу вендора. Требования модерации
   учтены: описание начинается с латиницы, структура — Plugin Model v2
   (content-модули).
