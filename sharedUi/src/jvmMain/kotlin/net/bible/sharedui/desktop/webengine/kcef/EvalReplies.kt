package net.bible.sharedui.desktop.webengine.kcef

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Evaluate-with-callback over a one-way message channel (pure logic, no CEF types). CEF's `executeJavaScript` returns
 * nothing, so [wrap] makes the page post `{"__eval": <id>, "v": <JSON of the result>}` back through the same channel as
 * the bridge messages, and [consume] hands such a reply to its callback instead of the bridge.
 *
 * The result mirrors Android's `evaluateJavascript`: the JSON of the value, `"null"` for `undefined`, a thrown error or
 * a value JSON cannot represent. Not thread-safe: the KCEF pane uses it on the UI thread only.
 */
internal class EvalReplies {
    private var nextId = 1L
    private val pending = mutableMapOf<Long, (String) -> Unit>()

    /** Registers [callback] and returns the script that evaluates [js] and posts the reply with [postMessageJs]. */
    fun wrap(js: String, postMessageJs: String, callback: (String) -> Unit): String {
        val id = nextId++
        pending[id] = callback
        return "(function () { var r; try { r = (0, eval)(" + jsStringLiteral(js) + "); } catch (e) { r = null; } " +
            "var v; try { v = JSON.stringify(r); } catch (e) { v = undefined; } " +
            "(" + postMessageJs + ")(JSON.stringify({__eval: " + id + ", v: v === undefined ? 'null' : v})); })();"
    }

    /** True when [message] is an eval reply (then its callback, if still pending, has run); false for bridge traffic. */
    fun consume(message: String): Boolean {
        if (!message.startsWith(REPLY_PREFIX)) return false
        val obj = try {
            Json.parseToJsonElement(message) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return true
        val id = obj["__eval"]?.jsonPrimitive?.longOrNull ?: return true
        val value = obj["v"]?.jsonPrimitive?.contentOrNull ?: "null"
        pending.remove(id)?.invoke(value)
        return true
    }

    /** Forgets every pending callback (pane disposed). */
    fun clear() = pending.clear()

    val pendingCount: Int get() = pending.size

    companion object {
        const val REPLY_PREFIX = "{\"__eval\":"

        /** [s] as a double-quoted JS string literal, safe for any content (line separators included). */
        fun jsStringLiteral(s: String): String = buildString(s.length + 2) {
            append('"')
            for (c in s) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == ' ' || c == ' ' || c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
