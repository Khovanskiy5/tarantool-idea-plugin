plugins {
    id("java")
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.khovanskiy"
version = "1.1.0"

// Шим JDBC-драйвера: отдельный jar в дистрибутиве, который пользователь
// добавляет в Driver Files источника данных. Компилируется против
// драйвера Tarantool с Maven Central, в jar кладутся только наши классы.
sourceSets {
    create("shim")
}

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

    "shimCompileOnly"("org.tarantool:connector:1.9.4")
}

val shimJar = tasks.register<Jar>("shimJar") {
    archiveBaseName = "tarantool-jdbc-shim"
    // Без версии в имени: на файл ссылается classpath драйвера в конфиге
    // IDE, и версионное имя протухало бы при каждом обновлении плагина.
    archiveVersion = ""
    from(sourceSets["shim"].output)
    // Сборка плагина дописывает дескриптор во все Jar-таски —
    // драйверному jar он не нужен.
    exclude("META-INF/plugin.xml")
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
