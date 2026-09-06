package net.bible.sharedcore.nav

/**
 * Routes for the Compose navigation graph (`:sharedUi`'s `NavHost`), and the typed builders that
 * are the ONLY sanctioned way to construct one.
 *
 * **Strings, deliberately (plan D1).** Type-safe `@Serializable` routes would require the
 * serialization plugin in `:sharedCore`, and this module is deliberately dependency-light — see
 * `download/CustomRepositoryModel.kt`, which declines `@Serializable` for the same reason. Every
 * live argument in the first migrated cluster is an id or a boolean, so the wire format costs
 * nothing. The typing is not lost, only moved: nothing should hand-concatenate a route, and
 * [encodeArg]/[decodeArg] exist so a free-text argument cannot corrupt one.
 *
 * Lives in `:sharedCore` rather than `:sharedUi` because it is pure data and this module already
 * has a `jvm()` target and a `commonTest` source set, so routes are testable on Linux in seconds.
 */
object NavRoutes {

    // Argument names. Each appears twice — in a PATTERN and in the read site — so both use these.
    const val ARG_PROMPT_ID: String = "promptId"
    const val ARG_PROMPT_TEMPLATE: String = "template"
    const val ARG_DEFAULT_CONTEXT: String = "defaultContext"
    const val ARG_EXECUTE_AFTER_SAVE: String = "executeAfterSave"
    const val ARG_START_EASY_SETUP: String = "startEasySetup"
    const val ARG_LOG_RECORD_ID: String = "logRecordId"
    const val ARG_WORKSPACE_ID: String = "workspaceId"

    // Argument-free routes: pattern and instance are the same string.
    const val AI_PROMPTS: String = "ai/prompts"
    const val AI_CONNECTION_SETTINGS: String = "ai/connectionSettings"
    const val AI_MODELS: String = "ai/models"
    const val AI_DOCUMENT_FILTER: String = "ai/documentFilter"
    const val AI_GLOBAL_TOOL_PERMISSIONS: String = "ai/globalToolPermissions"
    const val AI_TOOL_INFO: String = "ai/toolInfo"
    const val AI_RAW_LOG_HISTORY: String = "ai/rawLogHistory"

    // Parameterised routes: the PATTERN is what `composable(route = …)` registers; the builder
    // below is what a caller navigates to.
    const val AI_PROVIDERS_PATTERN: String =
        "ai/providers?$ARG_START_EASY_SETUP={$ARG_START_EASY_SETUP}"
    const val PROMPT_EDIT_PATTERN: String =
        "ai/promptEdit?$ARG_PROMPT_ID={$ARG_PROMPT_ID}" +
            "&$ARG_PROMPT_TEMPLATE={$ARG_PROMPT_TEMPLATE}" +
            "&$ARG_DEFAULT_CONTEXT={$ARG_DEFAULT_CONTEXT}" +
            "&$ARG_EXECUTE_AFTER_SAVE={$ARG_EXECUTE_AFTER_SAVE}"
    const val RAW_LLM_LOG_PATTERN: String =
        "ai/rawLlmLog?$ARG_LOG_RECORD_ID={$ARG_LOG_RECORD_ID}&$ARG_WORKSPACE_ID={$ARG_WORKSPACE_ID}"

    fun aiProviders(startEasySetup: Boolean = false): String =
        "ai/providers?$ARG_START_EASY_SETUP=$startEasySetup"

    fun promptEdit(
        promptId: String? = null,
        template: String? = null,
        defaultContext: String? = null,
        executeAfterSave: Boolean = false,
    ): String = buildRoute("ai/promptEdit") {
        optional(ARG_PROMPT_ID, promptId)
        optional(ARG_PROMPT_TEMPLATE, template)
        optional(ARG_DEFAULT_CONTEXT, defaultContext)
        required(ARG_EXECUTE_AFTER_SAVE, executeAfterSave.toString())
    }

    fun rawLlmLog(logRecordId: String? = null, workspaceId: String? = null): String =
        buildRoute("ai/rawLlmLog") {
            optional(ARG_LOG_RECORD_ID, logRecordId)
            optional(ARG_WORKSPACE_ID, workspaceId)
        }

    /**
     * Percent-encodes everything that is not an unreserved URI character. Hand-rolled rather than
     * pulled from a library because this must compile for iOS and JS as well as the JVM, and
     * `java.net.URLEncoder` is JVM-only.
     */
    fun encodeArg(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (byte in raw.encodeToByteArray()) {
            // A UTF-8 continuation/lead byte is always >= 0x80, i.e. negative as a signed Byte.
            // `byte.toInt()` sign-extends it (e.g. 0x80.toByte().toInt() == -128, not 128), so the
            // Char this produces is in 0xFF80..0xFFFF — nowhere near any range isUnreserved() tests
            // (all of which sit below 0x7F) — so every non-ASCII byte always falls to the escape
            // branch below. This is deliberate, not incidental: a future edit to isUnreserved() must
            // not add a range up there, or a raw high byte could start passing through unescaped.
            val c = byte.toInt().toChar()
            if (c.isUnreserved()) sb.append(c)
            else sb.append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
        return sb.toString()
    }

    /**
     * Inverse of [encodeArg]. Every route reaching this function was built by [encodeArg], so a
     * `%` that is not followed by exactly two hex digits — whether malformed or merely truncated
     * at the end of the string — means the input is corrupt or hand-crafted. Both deserve to fail
     * loudly: silently decoding a bad escape (or silently treating a truncated one as literal text)
     * would produce a plausible-looking but wrong string with nothing to catch it before it
     * navigates to a subtly wrong screen.
     */
    fun decodeArg(encoded: String): String {
        val out = ArrayList<Byte>(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '%') {
                require(i + 2 < encoded.length) {
                    "Malformed percent-encoding in \"$encoded\" at index $i: " +
                        "\"${encoded.substring(i)}\" is truncated (a % must be followed by two hex digits)"
                }
                val hi = encoded[i + 1]
                val lo = encoded[i + 2]
                require(hi.isHexDigit() && lo.isHexDigit()) {
                    "Malformed percent-encoding in \"$encoded\" at index $i: " +
                        "\"%$hi$lo\" is not a valid hex escape"
                }
                out.add(((hexVal(hi) shl 4) or hexVal(lo)).toByte())
                i += 3
            } else {
                for (b in c.toString().encodeToByteArray()) out.add(b)
                i += 1
            }
        }
        return out.toByteArray().decodeToString()
    }

    private const val HEX = "0123456789ABCDEF"

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' ||
            this == '_' || this == '.' || this == '~'

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        // Unreachable: every caller validates with isHexDigit() first. Throwing rather than
        // returning a sentinel keeps this function honest if that guarantee is ever broken.
        else -> throw IllegalArgumentException("Not a hex digit: '$c'")
    }

    private class RouteBuilder(private val base: String) {
        private val parts = mutableListOf<String>()
        fun optional(name: String, value: String?) {
            if (value != null) parts.add("$name=${encodeArg(value)}")
        }
        fun required(name: String, value: String) {
            parts.add("$name=${encodeArg(value)}")
        }
        fun build(): String = if (parts.isEmpty()) base else base + "?" + parts.joinToString("&")
    }

    private fun buildRoute(base: String, block: RouteBuilder.() -> Unit): String =
        RouteBuilder(base).apply(block).build()
}
