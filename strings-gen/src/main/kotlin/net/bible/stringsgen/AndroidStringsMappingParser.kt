package net.bible.stringsgen

/**
 * Recovers each `Strings` member's `R.string` key from the mechanical `override` declarations in
 * `AndroidStrings.kt`. The Android impl is rigidly generated, so a regex/token approach is enough —
 * but it must tolerate the real file's spacing variations (multi-line `get() =` vals, fun bodies
 * that wrap onto the next line, and funs WITHOUT an explicit `: String` return type). To absorb all
 * of that, the source is first normalized: comments and string literals are stripped, then every run
 * of whitespace (incl. newlines) collapses to one space.
 *
 * Two — and only two — `override` shapes are recognized:
 *  1. `override val NAME: String get() = context.getString(R.string.KEY)`
 *       → [StringMember]`(NAME, VAL, key = KEY)`
 *  2. `override fun NAME(PARAMS)[: String] = context.getString(R.string.KEY, …)`
 *       → [StringMember]`(NAME, FORMAT_FUN, key = KEY, params = <each "name: Type", top-level split>)`
 *
 * Any `override` member matching none of these throws [IllegalStateException] so drift fails loudly.
 *
 * JVM-only build-time code (like the other parsers here); its OUTPUT is what crosses into commonMain.
 */
fun parseAndroidStringsMapping(kt: String): List<StringMember> {
    val src = normalize(kt)
    val members = mutableListOf<StringMember>()

    // Each `override` declaration runs from "override " up to (but not including) the next "override "
    // (or end of input). The body of every shape is balanced w.r.t. the outermost `getString(` call,
    // so the next "override " token is a safe statement boundary.
    val starts = OVERRIDE.findAll(src).map { it.range.first }.toList()
    for ((i, start) in starts.withIndex()) {
        val end = starts.getOrNull(i + 1) ?: src.length
        // The LAST declaration carries the enclosing class's closing `}` (there is no `override`
        // after it to bound on); strip trailing `}`s so the construct's own `$` anchors still match.
        val decl = src.substring(start, end).trim().trimTrailingClassBraces()
        members += parseOverride(decl)
    }
    return members
}

private val OVERRIDE = Regex("""\boverride\s""")

/** Drops trailing `}` (+ surrounding spaces) left over from the enclosing class on the last decl.
 *  A real construct never *ends* in a bare `}` — VAL/FUN end in `)` — so a trailing `}` is always
 *  the class body close. */
private fun String.trimTrailingClassBraces(): String {
    var s = this.trimEnd()
    while (s.endsWith("}")) s = s.dropLast(1).trimEnd()
    return s
}

private val VAL = Regex(
    """^override\s+val\s+(\w+)\s*:\s*String\s+get\(\)\s*=\s*context\.getString\(\s*R\.string\.(\w+)\s*\)"""
)

// Fun with an OPTIONAL explicit `: String` return type (the real file omits it; the brief sample has it).
private val FUN = Regex(
    """^override\s+fun\s+(\w+)\s*\(([^)]*)\)\s*(?::\s*String\s*)?=\s*context\.getString\(\s*R\.string\.(\w+)\s*(?:,(.*))?\)\s*$"""
)

private fun parseOverride(decl: String): StringMember {
    VAL.matchEntire(decl)?.let { m ->
        return StringMember(m.groupValues[1], MemberKind.VAL, key = m.groupValues[2])
    }
    FUN.matchEntire(decl)?.let { m ->
        val name = m.groupValues[1]
        val params = splitTopLevel(m.groupValues[2]).map { it.trim() }.filter { it.isNotEmpty() }
        return StringMember(name, MemberKind.FORMAT_FUN, key = m.groupValues[3], params = params)
    }
    error(
        "AndroidStrings.kt `override` member matches none of the known VAL / FORMAT_FUN " +
            "shapes — the parser (and the iOS Strings generator) is out of sync with the file. " +
            "Offending declaration:\n  $decl"
    )
}

/** Splits a comma-separated param list on TOP-LEVEL commas only (commas inside `<…>`/`(…)` are kept). */
private fun splitTopLevel(s: String): List<String> {
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    var depth = 0
    for (c in s) {
        when (c) {
            '<', '(', '[' -> { depth++; sb.append(c) }
            '>', ')', ']' -> { if (depth > 0) depth--; sb.append(c) }
            ',' -> if (depth == 0) { parts.add(sb.toString()); sb.clear() } else sb.append(c)
            else -> sb.append(c)
        }
    }
    if (sb.isNotBlank()) parts.add(sb.toString())
    return parts
}

/**
 * Strips `//` line comments + block comments + string/char literals (so braces/parens/commas inside
 * them can't confuse the matchers), then collapses every whitespace run to a single space. The file
 * uses no string literals in its `override` declarations, but stripping them is cheap insurance.
 */
private fun normalize(kt: String): String {
    val sb = StringBuilder(kt.length)
    var i = 0
    val n = kt.length
    while (i < n) {
        val c = kt[i]
        when {
            c == '/' && i + 1 < n && kt[i + 1] == '/' -> {
                i += 2
                while (i < n && kt[i] != '\n') i++
            }
            c == '/' && i + 1 < n && kt[i + 1] == '*' -> {
                i += 2
                while (i + 1 < n && !(kt[i] == '*' && kt[i + 1] == '/')) i++
                i += 2
            }
            c == '"' || c == '\'' -> {
                // Skip the literal but emit a placeholder so an adjacent token isn't fused.
                val quote = c
                i++
                while (i < n && kt[i] != quote) {
                    if (kt[i] == '\\' && i + 1 < n) i++
                    i++
                }
                i++ // closing quote
                sb.append(' ')
            }
            else -> { sb.append(c); i++ }
        }
    }
    // Collapse all whitespace runs to a single space.
    return sb.toString().replace(Regex("""\s+"""), " ").trim()
}
