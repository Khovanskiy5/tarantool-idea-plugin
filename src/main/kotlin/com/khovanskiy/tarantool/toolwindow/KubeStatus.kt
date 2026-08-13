package com.khovanskiy.tarantool.toolwindow

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Разбор вывода `kubectl get pods -o json`: под — строка панели.
 *
 * Поля InstanceRow переиспользуются: pid — готовность контейнеров (1/1),
 * mode — счётчик рестартов, config — узел кластера. Заголовки колонок
 * панель подставляет свои, kubernetes-ные.
 */
object KubeStatus {

    fun parse(json: String): List<InstanceRow> {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) {
            return emptyList()
        }
        val items = root.asJsonObject.getAsJsonArray("items") ?: return emptyList()
        return items.mapNotNull { item ->
            val pod = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val metadata = pod.getAsJsonObject("metadata") ?: return@mapNotNull null
            val name = metadata.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            val containers = pod.getAsJsonObject("status")
                ?.getAsJsonArray("containerStatuses")
                ?.mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
                .orEmpty()
            val ready = containers.count { it.get("ready")?.asBoolean == true }
            val restarts = containers.sumOf { it.get("restartCount")?.asInt ?: 0 }
            InstanceRow(
                name = name,
                status = statusOf(pod, metadata, containers),
                pid = if (containers.isEmpty()) "" else "$ready/${containers.size}",
                mode = restarts.toString(),
                config = pod.getAsJsonObject("spec")
                    ?.get("nodeName")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                box = "",
            )
        }.sortedBy { it.name }
    }

    /**
     * Состояние пода. Причина ожидания контейнера (CrashLoopBackOff,
     * ImagePullBackOff…) информативнее фазы Pending/Running, а метка
     * удаления — это Terminating, которого в фазах kubectl нет.
     */
    private fun statusOf(pod: JsonObject, metadata: JsonObject, containers: List<JsonObject>): String {
        if (metadata.has("deletionTimestamp")) {
            return "Terminating"
        }
        containers.firstNotNullOfOrNull { container ->
            container.getAsJsonObject("state")
                ?.getAsJsonObject("waiting")
                ?.get("reason")?.takeIf { !it.isJsonNull }?.asString
        }?.let { return it }
        return pod.getAsJsonObject("status")
            ?.get("phase")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
    }
}
