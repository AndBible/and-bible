package net.bible.sharedcore.bibleview

import kotlin.jvm.JvmInline
import kotlinx.serialization.json.JsonPrimitive

/**
 * A raw JS expression inserted verbatim. Its producer guarantees validity; used for payloads whose
 * types still live in `:app` (documents' `contentJson()` is a JS literal, not JSON) — L2a spec R3.
 */
@JvmInline
value class JsExpr(val js: String)

/** One JS statement for BibleView. [plus] joins statements so they run in one evaluate, in order. */
@JvmInline
value class JsCall(val js: String) {
    operator fun plus(other: JsCall): JsCall = JsCall("$js;\n${other.js}")
}

/**
 * [value] as a JS string literal (JSON escaping: quote, backslash, control characters), also
 * escaping U+2028/U+2029, which end a string literal on pre-ES2019 WebViews (spec R6). `null` → `null`.
 */
fun jsString(value: String?): String =
    if (value == null) "null"
    else JsonPrimitive(value).toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
