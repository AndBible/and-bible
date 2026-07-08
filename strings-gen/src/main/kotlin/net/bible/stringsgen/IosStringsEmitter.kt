package net.bible.stringsgen

/**
 * The iOS `Strings` EMITTER. Produces the TEXT of two PURE-KOTLIN files that are compiled for
 * iOS (`iosArm64`/`iosSimulatorArm64`) — so the emitted code must be free of `platform.*`, `android.*`,
 * `R.*`, `String.format`, and any other JVM/Android-only API. The device-locale lookup is NOT emitted
 * here; the holder takes the locale tag as a constructor parameter.
 *
 *  - [emitStringsData] → `StringsData.kt`: the `localeTag → (key → value)` map literal + `lookup` /
 *    `fmt` helpers (top-level fns in the package, so the holder calls them unqualified).
 *  - [emitGeneratedStrings] → `GeneratedStrings.kt`: `internal class GeneratedStrings(tag) : Strings`
 *    implementing every interface member, + the `iosStrings(localeTag)` factory.
 *
 * JVM-only BUILD-TIME code (it just assembles strings); its OUTPUT is what crosses into commonMain.
 */

private const val PACKAGE = "net.bible.sharedui.strings"

/** Minimal CLDR quantity selection: 'one' for count==1 else 'other'.
 *  (AndBible's 5 plurals declare only one/other; full CLDR rules are future work.) */
fun selectQuantity(count: Int): String = if (count == 1) "one" else "other"

/** Verbatim source of [selectQuantity], emitted into `StringsData.kt` (mirrors the `fmt` twin pattern). */
const val SELECT_QUANTITY_SOURCE: String = """
fun selectQuantity(count: Int): String = if (count == 1) "one" else "other"
"""

/**
 * The hand-rolled `fmt` substitution logic, as a REAL JVM-callable reference function so a
 * `:strings-gen` unit test can execute it directly (the emitter prints [FMT_FUNCTION_SOURCE]
 * verbatim into `StringsData.kt`, whose body is kept byte-identical to this). Handles:
 *   - `%%` → `%`
 *   - `%n$s` / `%n$d` → arg `n` (1-based) as its string
 *   - `%n$[0]?<width>?[Xx]` → arg `n` formatted as hex (`X`=upper, `x`=lower), zero-padded to
 *     `width` (e.g. `0x%1${'$'}02X` with `0xF8` → `0xF8`, with `0x09` → `0x09`)
 * NO `String.format` (JVM-only) — a hand-rolled scan, so the emitted copy is iOS-safe.
 *
 * The body between the FMT-BODY-START/END markers is the SINGLE source of truth: [FMT_FUNCTION_SOURCE]
 * is kept byte-identical to it (modulo the `${'$'}` escapes a Kotlin string literal needs).
 */
// FMT-BODY-START
internal fun fmt(template: String, vararg args: Any?): String {
    val out = StringBuilder(template.length)
    var i = 0
    val n = template.length
    while (i < n) {
        val c = template[i]
        if (c != '%') {
            out.append(c)
            i++
            continue
        }
        // c == '%'
        if (i + 1 < n && template[i + 1] == '%') {
            out.append('%')
            i += 2
            continue
        }
        // Parse %<index>$<spec> — index is 1-based; spec is s/d, or [0]?<width>?[Xx] (hex).
        var j = i + 1
        var idx = 0
        var sawDigit = false
        while (j < n && template[j] in '0'..'9') {
            idx = idx * 10 + (template[j] - '0')
            sawDigit = true
            j++
        }
        if (sawDigit && j < n && template[j] == '$') {
            var k = j + 1
            // Optional leading '0' flag + width digits (e.g. "02" in "%1$02X").
            var width = 0
            var sawWidth = false
            while (k < n && template[k] in '0'..'9') {
                width = width * 10 + (template[k] - '0')
                sawWidth = true
                k++
            }
            if (k < n && (template[k] == 's' || template[k] == 'd')) {
                val arg = if (idx >= 1 && idx <= args.size) args[idx - 1] else null
                out.append(arg?.toString() ?: "")
                i = k + 1
            } else if (k < n && (template[k] == 'X' || template[k] == 'x')) {
                val arg = if (idx >= 1 && idx <= args.size) args[idx - 1] else null
                var hex = ((arg as? Int) ?: 0).toString(16)
                if (template[k] == 'X') hex = hex.uppercase()
                if (sawWidth) hex = hex.padStart(width, '0')
                out.append(hex)
                i = k + 1
            } else {
                // Not a recognized spec — emit the '%' verbatim and advance one char.
                out.append('%')
                i++
            }
        } else {
            // Not a recognized spec — emit the '%' verbatim and advance one char.
            out.append('%')
            i++
        }
    }
    return out.toString()
}
// FMT-BODY-END

/**
 * The verbatim Kotlin source of the [fmt] reference function above, emitted into `StringsData.kt`.
 * Keep byte-identical to [fmt]'s body (between the FMT-BODY-START/END markers) so the unit-tested
 * JVM logic equals the iOS-shipped logic.
 */
internal val FMT_FUNCTION_SOURCE: String =
    """
    |fun fmt(template: String, vararg args: Any?): String {
    |    val out = StringBuilder(template.length)
    |    var i = 0
    |    val n = template.length
    |    while (i < n) {
    |        val c = template[i]
    |        if (c != '%') {
    |            out.append(c)
    |            i++
    |            continue
    |        }
    |        // c == '%'
    |        if (i + 1 < n && template[i + 1] == '%') {
    |            out.append('%')
    |            i += 2
    |            continue
    |        }
    |        // Parse %<index>${'$'}<spec> — index is 1-based; spec is s/d, or [0]?<width>?[Xx] (hex).
    |        var j = i + 1
    |        var idx = 0
    |        var sawDigit = false
    |        while (j < n && template[j] in '0'..'9') {
    |            idx = idx * 10 + (template[j] - '0')
    |            sawDigit = true
    |            j++
    |        }
    |        if (sawDigit && j < n && template[j] == '${'$'}') {
    |            var k = j + 1
    |            // Optional leading '0' flag + width digits (e.g. "02" in "%1${'$'}02X").
    |            var width = 0
    |            var sawWidth = false
    |            while (k < n && template[k] in '0'..'9') {
    |                width = width * 10 + (template[k] - '0')
    |                sawWidth = true
    |                k++
    |            }
    |            if (k < n && (template[k] == 's' || template[k] == 'd')) {
    |                val arg = if (idx >= 1 && idx <= args.size) args[idx - 1] else null
    |                out.append(arg?.toString() ?: "")
    |                i = k + 1
    |            } else if (k < n && (template[k] == 'X' || template[k] == 'x')) {
    |                val arg = if (idx >= 1 && idx <= args.size) args[idx - 1] else null
    |                var hex = ((arg as? Int) ?: 0).toString(16)
    |                if (template[k] == 'X') hex = hex.uppercase()
    |                if (sawWidth) hex = hex.padStart(width, '0')
    |                out.append(hex)
    |                i = k + 1
    |            } else {
    |                // Not a recognized spec — emit the '%' verbatim and advance one char.
    |                out.append('%')
    |                i++
    |            }
    |        } else {
    |            // Not a recognized spec — emit the '%' verbatim and advance one char.
    |            out.append('%')
    |            i++
    |        }
    |    }
    |    return out.toString()
    |}
    """.trimMargin()

/**
 * Escapes [s] for safe inclusion inside a Kotlin `"..."` string literal. The order matters: `\` first
 * (so escapes we add aren't double-escaped). `$` MUST be escaped (Android values contain `%1$s` etc.;
 * an unescaped `$` would be read as a string template). Also handles `"`, newline, tab, CR.
 */
internal fun kotlinStringLiteral(s: String): String {
    val sb = StringBuilder(s.length + 2)
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '$' -> sb.append("\\$")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

/** A `"key" to "value"` map entry with both sides escaped as Kotlin string literals. */
private fun entryLiteral(key: String, value: String): String =
    "\"${kotlinStringLiteral(key)}\" to \"${kotlinStringLiteral(value)}\""

/**
 * Emits the `StringsData.kt` text:
 *  - `internal object StringsData` holding the `localeTag → (key → value)` map literal.
 *  - top-level `fun lookup(tag, key)`: exact tag → language-only prefix (e.g. `pt-BR`→`pt`) →
 *    `"en"` → the key itself, as fallbacks.
 *  - top-level `fun fmt(template, vararg args)`: positional `%n$conv` (conv ∈ s/d) substitution, plus
 *    hex `%n$[0]?<width>?[Xx]` (e.g. `0x%1${'$'}02X`), `%%`→`%`, NO `String.format` (JVM-only) — a
 *    hand-rolled scan. Emitted verbatim from the JVM-callable reference [fmt] (unit-tested).
 */
fun emitStringsData(
    localeToKeyValues: Map<String, Map<String, String>>,
    localeToPlurals: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    localeToArrays: Map<String, Map<String, List<String>>> = emptyMap(),
): String {
    val sb = StringBuilder()
    sb.append("package ").append(PACKAGE).append("\n\n")
    sb.append("// GENERATED by :strings-gen (IosStringsEmitter). DO NOT EDIT.\n\n")

    sb.append("internal object StringsData {\n")
    sb.append("    val data: Map<String, Map<String, String>> = mapOf(\n")
    for ((tag, kv) in localeToKeyValues) {
        sb.append("        \"").append(kotlinStringLiteral(tag)).append("\" to mapOf(\n")
        for ((key, value) in kv) {
            sb.append("            ").append(entryLiteral(key, value)).append(",\n")
        }
        sb.append("        ),\n")
    }
    sb.append("    )\n\n")

    // plurals: tag → name → quantity → template.
    sb.append("    val plurals: Map<String, Map<String, Map<String, String>>> = mapOf(\n")
    for ((tag, byName) in localeToPlurals) {
        sb.append("        \"").append(kotlinStringLiteral(tag)).append("\" to mapOf(\n")
        for ((name, byQty) in byName) {
            sb.append("            \"").append(kotlinStringLiteral(name)).append("\" to mapOf(\n")
            for ((qty, template) in byQty) {
                sb.append("                ").append(entryLiteral(qty, template)).append(",\n")
            }
            sb.append("            ),\n")
        }
        sb.append("        ),\n")
    }
    sb.append("    )\n\n")

    // arrays: tag → name → ordered items.
    sb.append("    val arrays: Map<String, Map<String, List<String>>> = mapOf(\n")
    for ((tag, byName) in localeToArrays) {
        sb.append("        \"").append(kotlinStringLiteral(tag)).append("\" to mapOf(\n")
        for ((name, items) in byName) {
            sb.append("            \"").append(kotlinStringLiteral(name)).append("\" to listOf(")
            sb.append(items.joinToString(", ") { "\"${kotlinStringLiteral(it)}\"" })
            sb.append("),\n")
        }
        sb.append("        ),\n")
    }
    sb.append("    )\n")
    sb.append("}\n\n")

    // lookup: exact tag → language-only prefix → "en" → the key itself.
    sb.append(
        """
        |fun lookup(tag: String, key: String): String {
        |    StringsData.data[tag]?.get(key)?.let { return it }
        |    val dash = tag.indexOf('-')
        |    if (dash > 0) {
        |        StringsData.data[tag.substring(0, dash)]?.get(key)?.let { return it }
        |    }
        |    StringsData.data["en"]?.get(key)?.let { return it }
        |    return key
        |}
        |
        """.trimMargin()
    )
    sb.append("\n")

    // fmt: hand-rolled %n$conv scan (NO String.format — JVM-only). conv ∈ s/d; hex %n$[0]?<width>?[Xx];
    // %% → %. Emitted verbatim from the JVM-callable reference [fmt] (kept byte-identical, unit-tested).
    sb.append(FMT_FUNCTION_SOURCE)
    sb.append("\n")

    // selectQuantity: minimal CLDR (one for count==1 else other). Emitted verbatim from [selectQuantity].
    sb.append(SELECT_QUANTITY_SOURCE)
    sb.append("\n")

    // lookupPlural: quantity-selected plural template with the same tag → lang → "en" fallback as
    // `lookup` (falls back to "other" within a locale if the selected quantity is absent). The CALLER
    // (generated override) `fmt`s the returned template with the plural's format args.
    sb.append(
        """
        |fun lookupPlural(tag: String, name: String, count: Int): String {
        |    val qty = selectQuantity(count)
        |    fun pick(t: String): String? {
        |        val byName = StringsData.plurals[t]?.get(name) ?: return null
        |        return byName[qty] ?: byName["other"]
        |    }
        |    pick(tag)?.let { return it }
        |    val dash = tag.indexOf('-')
        |    if (dash > 0) pick(tag.substring(0, dash))?.let { return it }
        |    pick("en")?.let { return it }
        |    return name
        |}
        |
        """.trimMargin()
    )
    sb.append("\n")

    // lookupArray: ordered string-array items with the same tag → lang → "en" fallback.
    sb.append(
        """
        |fun lookupArray(tag: String, name: String): List<String> {
        |    StringsData.arrays[tag]?.get(name)?.let { return it }
        |    val dash = tag.indexOf('-')
        |    if (dash > 0) StringsData.arrays[tag.substring(0, dash)]?.get(name)?.let { return it }
        |    StringsData.arrays["en"]?.get(name)?.let { return it }
        |    return emptyList()
        |}
        |
        """.trimMargin()
    )
    sb.append("\n")
    return sb.toString()
}

/** Param NAME from a decl like `"price: String"` → `"price"` (substring before `:`, trimmed). */
private fun paramName(decl: String): String = decl.substringBefore(':').trim()

/**
 * Emits the `GeneratedStrings.kt` text: `internal class GeneratedStrings(private val tag: String) :
 * Strings` implementing EVERY interface member by name, plus the `iosStrings(localeTag)` factory.
 *  - VAL → `override val NAME: String get() = lookup(tag, "KEY")`
 *  - FORMAT_FUN → `override <signature> = fmt(lookup(tag, "KEY"), p1, p2…)`
 *
 * Throws if any interface member has NO entry in [mappingByName] (coverage failure surfaces here, at
 * generation, not at iOS compile time).
 */
fun emitGeneratedStrings(
    interfaceMembers: List<InterfaceMember>,
    mappingByName: Map<String, StringMember>,
): String {
    val sb = StringBuilder()
    sb.append("package ").append(PACKAGE).append("\n\n")
    sb.append("// GENERATED by :strings-gen (IosStringsEmitter). DO NOT EDIT.\n\n")
    sb.append("internal class GeneratedStrings(private val tag: String) : Strings {\n")

    for (member in interfaceMembers) {
        val mapping = mappingByName[member.name]
            ?: error(
                "Interface member '${member.name}' has no entry in the AndroidStrings.kt mapping — " +
                    "the iOS Strings generator cannot implement it. Add the `override` for it in " +
                    "AndroidStrings.kt (every Strings member must be backed by an R.string key)."
            )

        when (mapping.kind) {
            MemberKind.VAL -> {
                sb.append("    override val ").append(member.name)
                    .append(": String get() = lookup(tag, \"")
                    .append(kotlinStringLiteral(mapping.key)).append("\")\n")
            }

            MemberKind.FORMAT_FUN -> {
                val argList = mapping.params.joinToString(", ") { paramName(it) }
                val args = if (argList.isEmpty()) "" else ", $argList"
                sb.append("    override ").append(member.signature)
                    .append(" = fmt(lookup(tag, \"")
                    .append(kotlinStringLiteral(mapping.key)).append("\")").append(args).append(")\n")
            }

            MemberKind.PLURAL_FUN -> {
                val argList = mapping.pluralFmtArgs.joinToString(", ")
                val fmtCall = if (argList.isEmpty()) "fmt(it)" else "fmt(it, $argList)"
                sb.append("    override ").append(member.signature)
                    .append(" = lookupPlural(tag, \"")
                    .append(kotlinStringLiteral(mapping.key)).append("\", ")
                    .append(mapping.pluralCountArg).append(").let { ").append(fmtCall).append(" }\n")
            }

            MemberKind.ARRAY_VAL -> {
                sb.append("    override val ").append(member.name)
                    .append(": List<String> get() = lookupArray(tag, \"")
                    .append(kotlinStringLiteral(mapping.key)).append("\")\n")
            }
        }
    }

    sb.append("}\n\n")
    sb.append("fun iosStrings(localeTag: String): Strings = GeneratedStrings(localeTag)\n")
    return sb.toString()
}
