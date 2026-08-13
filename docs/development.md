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
│   └── TarantoolConfigSchemaProviderFactory.kt   config.yaml + tt.yaml
├── wal/                      файлы данных
│   ├── TarantoolWalFileType.kt            тип .snap/.xlog/.vylog
│   ├── WalFileEditorProvider.kt           регистрация редактора
│   └── WalPreviewFileEditor.kt            read-only viewer поверх tt cat
├── project/                  мастер нового проекта
│   ├── TarantoolNewProjectWizard.kt       пункт в IDEA (newProjectWizard.generator)
│   ├── TarantoolProjectGenerator.kt       пункт в PyCharm/WebStorm (directoryProjectGenerator)
│   └── TtScaffolder.kt                    общий запуск tt init/tt create
├── templates/
│   └── TarantoolLuaTemplateContext.kt     контекст live-шаблонов (.lua)
├── debugger/
│   └── SetupEmmyDebuggerAction.kt         раскладка emmy_debug.lua и конфигурации attach
├── settings/
│   ├── TarantoolSettings.kt               PersistentStateComponent (пути)
│   ├── TarantoolSettingsConfigurable.kt   страница Settings → Tools → Tarantool
│   ├── TarantoolProjectSettings.kt        проектный режим запуска (.idea/tarantool.xml)
│   └── TarantoolProjectConfigurable.kt    подстраница «Режим запуска»
└── stubs/
    └── GenerateTarantoolStubsAction.kt    генерация типов для EmmyLua2

src/main/resources/
├── META-INF/plugin.xml            манифест, регистрация расширений
├── META-INF/tarantool-json.xml    часть, зависящая от JSON-модуля (схемы)
├── schemas/tarantool-config.json  схема config.yaml (из config:jsonschema())
├── schemas/tt-config.json         схема tt.yaml (по исходникам tt)
├── liveTemplates/Tarantool.xml    live-шаблоны
├── stubs/gen_stubs.lua            генератор типов (запускается tarantool'ом)
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
| `postStartupActivity` ×2 | `TarantoolTypesStartup`, `TarantoolDataSourceStartup`** | предложение типов; автонастройка драйвера и источников данных |
| `notificationGroup` | — | всплывающие уведомления |
| `runLineMarkerContributor`*** | `TarantoolLuaRunLineMarkerContributor` | гуттер ▶ на .lua |
| `JavaScript.JsonSchema.ProviderFactory`* | `TarantoolConfigSchemaProviderFactory` | схемы YAML |
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

**Отладка без своего протокола.** Кнопка Debug подавлена
(`RunConfigurationWithSuppressedDefaultDebugAction`): пошаговую отладку
дают либо `tarantool -d` (интерактивная консоль в PTY), либо плагин
EmmyLua2. Реализация собственного XDebugger-протокола не окупается.

**Без DataGrip-интеграции.** SQL-консоль сделана через
`tt connect -l sql`: API Database-инструментов закрытый и есть только
в Ultimate — зависимость, на которой умер официальный плагин Tarantool.

**Схемы как ресурсы.** Схема config.yaml генерируется самим Tarantool
(`require('config'):jsonschema()`) и кладётся в ресурсы как есть; схема
tt.yaml написана вручную по структуре `CliOpts` (cli/config/config.go).
Обновление при выходе новых версий: перегенерировать/сверить и заменить
файл в `schemas/`.

**Типы для EmmyLua2 — генерация, а не поставка.** API снимается
интроспекцией с интерпретатора пользователя, поэтому описания всегда
соответствуют установленной версии Tarantool; плагин не обязан выпускать
релиз под каждую версию сервера.

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
в `resources/stubs/gen_stubs.lua`.

## Релиз

1. Поднять `version` в `build.gradle.kts`, дописать `<change-notes>`
   в plugin.xml.
2. `./gradlew clean test buildPlugin verifyPlugin` — верификатор должен
   ответить «Compatible» без списка устаревших API.
3. Тег `v<версия>`, zip из `build/distributions` — артефакт релиза.
4. Для Marketplace: заполнить страницу вендора. Требования модерации
   учтены: описание начинается с латиницы, структура — Plugin Model v2
   (content-модули).
