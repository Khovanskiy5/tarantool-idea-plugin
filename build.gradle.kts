plugins {
    id("java")
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.khovanskiy"
version = "1.4.0"

// Шим JDBC-драйвера: единственный jar в дистрибутиве, который подключается
// в Driver Files источника данных. Коннектор Tarantool шейдится внутрь,
// а его MsgPackLite замещается нашим форком с поддержкой ext-типов
// (datetime, uuid, decimal, interval) — оригинал валил соединение на
// первом же таком значении. Отдельный артефакт коннектора с Maven Central
// в classpath драйвера больше не нужен и вреден: он перекрывал бы форк.
sourceSets {
    create("shim")
}

// Зависимости, которые распаковываются внутрь jar шима.
val shimShade: Configuration by configurations.creating

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Локальная сборка идёт против установленной IDE — без загрузки дистрибутива
// платформы (~1 ГБ). В CI локальной IDE нет, там скачивается IC.
val localIde = file("/Applications/IntelliJ IDEA.app")
val useLocalIde = providers.gradleProperty("useLocalIde").map(String::toBoolean).getOrElse(true) &&
    localIde.exists()

dependencies {
    intellijPlatform {
        if (useLocalIde) {
            local(localIde)
        } else {
            // Ultimate, а не Community: SQL-модулю нужны классы
            // Database-плагина; для компиляции лицензия не требуется.
            intellijIdeaUltimate("2026.1.3")
        }
        // Классы JSON Schema (JsonSchemaProviderFactory) живут в JSON-плагине.
        bundledPlugin("com.intellij.modules.json")
        // Классы SQL-диалектов (SqlLanguageDialectBase, Dbms) — в Database-плагине.
        bundledPlugin("com.intellij.database")
        // Открытие консоли инстанса во встроенном Терминале.
        bundledPlugin("org.jetbrains.plugins.terminal")
    }

    testImplementation(kotlin("stdlib"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // В main Gson приходит с платформой; тестам нужен свой экземпляр.
    testImplementation("com.google.code.gson:gson:2.11.0")

    "shimCompileOnly"("org.tarantool:connector:1.9.4")
    shimShade("org.tarantool:connector:1.9.4")
}

// Курированные EmmyLua-аннотации (annotations/tarantool, копия набора
// из tarantool-vscode) упаковываются в один zip-ресурс: плагин разворачивает
// его в .types/tarantool/bundled/ проекта. Zip вместо россыпи файлов —
// чтобы не вести в коде список из полусотни имён: содержимое перечисляет
// сам архив.
val annotationsZip = tasks.register<Zip>("annotationsZip") {
    archiveFileName = "tarantool-annotations.zip"
    destinationDirectory = layout.buildDirectory.dir("tmp/annotations")
    from("annotations/tarantool")
}

tasks.processResources {
    from(annotationsZip) {
        into("stubs")
    }
}

val shimJar = tasks.register<Jar>("shimJar") {
    archiveBaseName = "tarantool-jdbc-shim"
    // Без версии в имени: на файл ссылается classpath драйвера в конфиге
    // IDE, и версионное имя протухало бы при каждом обновлении плагина.
    archiveVersion = ""
    from(sourceSets["shim"].output)
    from(provider { shimShade.map { zipTree(it) } }) {
        // Оригинальные MsgPackLite и SQLMsgPackLite замещаются форками
        // из sourceSets["shim"]
        exclude("org/tarantool/MsgPackLite.class")
        exclude("org/tarantool/MsgPackLite$*.class")
        exclude("org/tarantool/jdbc/SQLMsgPackLite.class")
        exclude("org/tarantool/jdbc/SQLMsgPackLite$*.class")
        exclude("META-INF/MANIFEST.MF")
        // Services-файл коннектора регистрировал бы в DriverManager сырой
        // SQLDriver мимо обёртки; ресурсы шима кладут свой файл с ShimDriver
        exclude("META-INF/services/java.sql.Driver")
    }
    // Сборка плагина дописывает дескриптор во все Jar-таски —
    // драйверному jar он не нужен.
    exclude("META-INF/plugin.xml")
}

// Тесты MsgPackLite гоняются против собранного jar шима — того же
// артефакта, что едет пользователю; заодно проверяется, что шейдинг
// действительно заменил оригинальный MsgPackLite форком.
dependencies {
    testImplementation(files(shimJar))
}

// Jar шима кладётся в отдельный каталог driver/ дистрибутива: в lib/
// сборка плагина вписывает plugin.xml в каждый jar, и верификатор
// отверг бы дистрибутив с двумя дескрипторами. Классы шима нужны только
// драйверному classpath источника данных, не плагину.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox") {
    from(shimJar) {
        into("tarantool-idea-plugin/driver")
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.khovanskiy.tarantool"
        name = "Tarantool"
        ideaVersion {
            sinceBuild = "261"
        }
    }

    // Инструментация форм и @NotNull не используется — отключаем,
    // чтобы не тянуть компилятор форм.
    instrumentCode = false

    // Страницы настроек у плагина нет — индекс searchable options не нужен.
    buildSearchableOptions = false

    pluginVerification {
        ides {
            if (useLocalIde) {
                local(localIde)
            } else {
                create("IU", "2026.1.3")
            }
        }
    }
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>("verifyPlugin") {
    // SQL-диалект сознательно опирается на модуль intellij.database.dialects.sql92:
    // это единственный открытый для наследования SQL-парсер, замены нет.
    // Verifier помечает зависимость на internal-модуль как compatibility
    // problem («может сломаться в будущих версиях») — риск принят и
    // задокументирован в docs/development.md, поэтому сборку валят только
    // структурные дефекты плагина.
    failureLevel = listOf(
        org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
    )
}

tasks.test {
    useJUnitPlatform()
}
