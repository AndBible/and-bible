package net.bible.service.llm.tools

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

/** Framework-compatible optional string coercion, including explicit JSON null as "null". */
fun JsonObject.optString(key: String, fallback: String = ""): String =
    this[key]?.frameworkString() ?: fallback

fun JsonObject.optInt(key: String, fallback: Int = 0): Int {
    val value = this[key] as? JsonPrimitive ?: return fallback
    return if (value.isString) value.content.toDoubleOrNull()?.toInt() ?: fallback
    else value.content.toLongOrNull()?.toInt() ?: value.content.toDoubleOrNull()?.toInt() ?: fallback
}

fun JsonObject.optBoolean(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.content?.let {
        when {
            it.equals("true", ignoreCase = true) -> true
            it.equals("false", ignoreCase = true) -> false
            else -> fallback
        }
    } ?: fallback

fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray
fun JsonObject.has(key: String): Boolean = containsKey(key)
fun JsonObject.isNull(key: String): Boolean = this[key] == null || this[key] == JsonNull
fun JsonArray.stringAt(index: Int): String = this[index].let {
    it.frameworkString()
}

private fun JsonElement.frameworkString(): String = when (this) {
    is JsonPrimitive -> if (isString || this == JsonNull || booleanOrNull != null) content
        else content.toLongOrNull()?.toString() ?: content.toDouble().toString()
    else -> frameworkJson()
}

// Framework object/array text normalizes numbers and escapes slashes, unlike kotlinx JSON.
internal fun JsonElement.frameworkJson(): String = when (this) {
    is JsonObject -> entries.joinToString(",", "{", "}") { (key, value) ->
        JsonPrimitive(key).toString().replace("/", "\\/") + ":" + value.frameworkJson()
    }
    is JsonArray -> joinToString(",", "[", "]") { it.frameworkJson() }
    is JsonPrimitive -> when {
        isString -> toString().replace("/", "\\/")
        this == JsonNull || booleanOrNull != null -> content
        else -> frameworkString().removeSuffix(".0")
    }
}

private val prettyPrinter = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** Render JSON for raw log views without depending on the Android JSON framework. */
fun prettyJson(text: String): String = prettyPrinter
    .encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))

fun parseToolArguments(text: String): JsonObject {
    if (text.isBlank()) return JsonObject(emptyMap())
    return Json.parseToJsonElement(text) as? JsonObject
        ?: throw SerializationException("Tool arguments must be a JSON object")
}
