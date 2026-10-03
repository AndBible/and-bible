package net.bible.stringsgen

/** One member declared in the `Strings` interface body, recovered from `Strings.kt` itself.
 *  - [name]: the member identifier.
 *  - [isFun]: true for a `fun NAME(...): String`, false for a `val NAME: String`.
 *  - [signature]: the verbatim, single-line-collapsed declaration (e.g.
 *    `fun fullVersionBuyButton(price: String): String`, `val appName: String`) — re-emitted by the
 *    emitter as the `override` signature, and the name set is the coverage cross-check against
 *    [parseAndroidStringsMapping]. */
data class InterfaceMember(val name: String, val isFun: Boolean, val signature: String)

/**
 * Parses the `Strings` interface declaration, returning one [InterfaceMember] per `val NAME: String`
 * and `fun NAME(...): String` member in declaration order. Everything else is ignored: KDoc / line
 * comments, imports, the `interface Strings {` / `}` braces, blank lines, and the trailing top-level
 * `val LocalStrings = staticCompositionLocalOf<Strings> { … }` (it has an initializer, so it never
 * matches the bare `val NAME: String` member shape).
 *
 * Robustness: comments and string literals are stripped first (so a `val` mentioned inside a KDoc or
 * comment can't masquerade as a member), then a multi-line `fun` signature is joined onto one line
 * (its whitespace runs collapsed to single spaces) before matching, so wrapped param lists are
 * recovered verbatim for re-emission. JVM-only build-time code; its OUTPUT crosses into commonMain.
 */
fun parseStringsInterface(kt: String): List<InterfaceMember> {
    val src = stripCommentsAndLiterals(kt)
    val members = mutableListOf<InterfaceMember>()

    // Tokenize into top-level declaration-ish units: a `fun` runs from "fun " to the closing `)`
    // of its (possibly multi-line) param list plus the `: String` return type; a `val` is one
    // logical line. Rather than track scope, scan with regexes over the whole stripped source —
    // the two member shapes are unambiguous and the `LocalStrings` val carries an `=`, so a bare
    // `val NAME: String` (no `=`, no `(`) can only be an interface member.
    for (line in src.lineSequence()) {
        val m = VAL_MEMBER.find(line) ?: LIST_VAL_MEMBER.find(line) ?: continue
        members += InterfaceMember(m.groupValues[1], isFun = false, signature = line.trim())
    }

    // `fun` signatures may wrap across lines; collapse the whole stripped source to single spaces
    // and pull each `fun NAME(...): String` out (the `(...)` is matched non-greedily up to `): String`).
    val collapsed = src.replace(Regex("""\s+"""), " ")
    for (f in FUN_MEMBER.findAll(collapsed)) {
        members += InterfaceMember(f.groupValues[1], isFun = true, signature = f.value.trim())
    }

    return members
}

/** `val NAME: String` with NOTHING after `String` — excludes `val X: String get() = …`,
 *  `val LocalStrings = …`, and any initialized/computed property. Anchored to a full line. */
private val VAL_MEMBER = Regex("""^\s*val\s+(\w+)\s*:\s*String\s*$""")

/** `val NAME: List<String>` interface member (a `<string-array>`-backed accessor). Same
 *  no-initializer, full-line shape as [VAL_MEMBER]. */
private val LIST_VAL_MEMBER = Regex("""^\s*val\s+(\w+)\s*:\s*List<String>\s*$""")

/** `fun NAME(<anything, incl. wrapped>): String` — non-greedy params, matched over the
 *  whitespace-collapsed source so a multi-line signature is one match. */
private val FUN_MEMBER = Regex("""fun\s+(\w+)\s*\(.*?\)\s*:\s*String""")

/**
 * Strips `//` line comments, block comments, and string/char literals, leaving everything else
 * (incl. newlines and indentation) intact so per-line `val` matching still works. Literals become a
 * single space so adjacent tokens don't fuse.
 */
private fun stripCommentsAndLiterals(kt: String): String {
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
                while (i + 1 < n && !(kt[i] == '*' && kt[i + 1] == '/')) {
                    // Preserve newlines inside block comments so line numbers / per-line matching hold.
                    if (kt[i] == '\n') sb.append('\n')
                    i++
                }
                i += 2
            }
            c == '"' || c == '\'' -> {
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
    return sb.toString()
}
