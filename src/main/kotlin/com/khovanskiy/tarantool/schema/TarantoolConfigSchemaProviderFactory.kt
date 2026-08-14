package com.khovanskiy.tarantool.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.khovanskiy.tarantool.settings.TarantoolProjectSettings
import java.io.File

/**
 * Подключает JSON-схемы Tarantool к YAML-файлам:
 *  - config.yaml — кластерная конфигурация, схема снята с Tarantool 3.8
 *    вызовом require('config'):jsonschema();
 *  - tt.yaml — окружение tt, схема составлена по структуре CliOpts
 *    из исходников tt 2.14.
 *
 * Через схему YAML-редактор получает автодополнение имён опций,
 * документацию и проверку типов значений.
 */
class TarantoolConfigSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> =
        mutableListOf(TarantoolConfigSchemaProvider(project), TtConfigSchemaProvider())
}

/** Схема tt.yaml: применяется по имени файла, оно у tt фиксированное. */
class TtConfigSchemaProvider : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean = file.name in TT_CONFIG_NAMES

    override fun getName(): String = "tt environment config"

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(TtConfigSchemaProvider::class.java, "/schemas/tt-config.json")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_2020_12

    private companion object {
        val TT_CONFIG_NAMES = setOf("tt.yaml", "tt.yml")
    }
}

class TarantoolConfigSchemaProvider(private val project: Project) : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean = isSchemaTarget(project, file)

    override fun getName(): String = "Tarantool cluster config"

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(TarantoolConfigSchemaProvider::class.java, SCHEMA_RESOURCE)

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_2020_12

    companion object {
        private const val SCHEMA_RESOURCE = "/schemas/tarantool-config.json"

        /** Имена, требующие подтверждения контекстом tt или пользователем. */
        val CONFIG_NAMES = setOf("config.yaml", "config.yml")

        /**
         * Имена, специфичные для Tarantool сами по себе: cluster.yml —
         * кластерная конфигурация, source.yml — файл-источник конфигурации
         * (например, выгружаемый в etcd). Ложные срабатывания маловероятны,
         * поэтому схема применяется без дополнительных условий — как
         * и в официальном VS Code-расширении.
         */
        val UNCONDITIONAL_NAMES = setOf("cluster.yaml", "cluster.yml", "source.yaml", "source.yml")

        private val INSTANCES_NAMES = setOf("instances.yml", "instances.yaml")

        /**
         * config.yaml получает схему внутри проекта tt (рядом instances.yml
         * либо в корне tt.yaml) или по явному согласию пользователя через
         * баннер редактора — иначе схема цеплялась бы к любому config.yaml,
         * в том числе чужих стеков.
         */
        fun isSchemaTarget(project: Project, file: VirtualFile): Boolean {
            if (file.name in UNCONDITIONAL_NAMES) {
                return true
            }
            if (file.name !in CONFIG_NAMES) {
                return false
            }
            if (isInTtContext(project, file)) {
                return true
            }
            return TarantoolProjectSettings.getInstance(project).isSchemaEnabled(file.url)
        }

        fun isInTtContext(project: Project, file: VirtualFile): Boolean {
            val sibling = file.parent
            if (sibling != null && INSTANCES_NAMES.any { sibling.findChild(it) != null }) {
                return true
            }
            val basePath = project.basePath ?: return false
            return File(basePath, "tt.yaml").exists() || File(basePath, "tt.yml").exists()
        }
    }
}
