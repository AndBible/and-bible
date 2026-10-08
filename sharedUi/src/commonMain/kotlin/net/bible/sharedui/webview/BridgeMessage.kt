package net.bible.sharedui.webview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One `window.android.<method>(args…)` call from BibleView, as posted by the iOS shim. */
data class BridgeMessage(val method: String, val args: List<JsonElement>)

sealed interface BridgeResult {
    data object Handled : BridgeResult
    data class Unhandled(val method: String) : BridgeResult
    data class Error(val reason: String) : BridgeResult
}

/** Parses the shim's `{"method": String, "args": Array}` payload; never throws. */
fun parseBridgeMessage(raw: String): Result<BridgeMessage> = runCatching {
    val obj = Json.parseToJsonElement(raw) as? JsonObject ?: error("not an object")
    val method = (obj["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("missing method")
    val args = obj["args"] as? JsonArray ?: error("args is not an array")
    BridgeMessage(method, args.toList())
}
