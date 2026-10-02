package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F109: an icon-only control must carry a label for TalkBack, and a toggle row must expose one
 * node, not two. Source scan; decorative icons inside an already-labelled control are listed in
 * `src/test/resources/icon-cd-allowlist.txt` as `path:line # reason`.
 */
class IconContentDescriptionGuardTest {
    private val roots = listOf(File("../sharedUi/src/commonMain/kotlin"), File("src/main/java"))
    private val allow = File("src/test/resources/icon-cd-allowlist.txt").readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * `IconButton(…) { Icon(…, contentDescription = null …` within one call. Each `Icon(` call is
     * measured by bracket matching (a `[^)]*` regex stops at the first nested `if (…)` and missed
     * the favourite stars).
     */
    internal fun unlabelledIconButtons(path: String, src: String): List<String> =
        Regex("""IconButton\s*\(""").findAll(src).mapNotNull { m ->
            val body = blockAfter(src, m.range.last) ?: return@mapNotNull null
            val unlabelledIcon = Regex("""\bIcon\s*\(""").findAll(body).any { i ->
                val call = argsAfter(body, i.range.last) ?: return@any false
                Regex("""contentDescription\s*=\s*null\b""").containsMatchIn(call)
            }
            if (unlabelledIcon && !Regex("""Modifier[^)]*semantics""").containsMatchIn(body)) "$path:${lineOf(src, m.range.first)}" else null
        }.toList()

    /**
     * A Checkbox/Switch with a non-null `onCheckedChange` and no label of its own: beside a `Text` in
     * a row it becomes a second, unlabelled TalkBack node. The row must be `toggleable(role = …)`
     * and the control `onCheckedChange = null`, which merges them into one labelled node.
     */
    internal fun doubleToggleNodes(path: String, src: String): List<String> =
        Regex("""\b(Checkbox|Switch)\s*\(""").findAll(src).mapNotNull { m ->
            val call = argsAfter(src, m.range.last) ?: return@mapNotNull null
            val nonNull = Regex("""onCheckedChange\s*=\s*(?!null\b)\S""").containsMatchIn(call)
            if (nonNull && !call.contains("contentDescription")) "$path:${lineOf(src, m.range.first)}" else null
        }.toList()

    /** Index of the bracket closing the one at [open] (same kind), or -1. */
    private fun matching(src: String, open: Int): Int {
        val o = src[open]; val c = if (o == '(') ')' else '}'
        var depth = 0
        for (i in open until src.length) {
            if (src[i] == o) depth++ else if (src[i] == c) { depth--; if (depth == 0) return i }
        }
        return -1
    }

    /** The call's `( … )` -- [parenIdx] is the index of its '('. */
    internal fun argsAfter(src: String, parenIdx: Int): String? =
        matching(src, parenIdx).takeIf { it > 0 }?.let { src.substring(parenIdx, it + 1) }

    /** The call's `( … )` plus its trailing lambda `{ … }`, if one follows the ')'. */
    internal fun blockAfter(src: String, parenIdx: Int): String? {
        val close = matching(src, parenIdx).takeIf { it > 0 } ?: return null
        var j = close + 1
        while (j < src.length && src[j].isWhitespace()) j++
        if (j >= src.length || src[j] != '{') return src.substring(parenIdx, close + 1)
        val end = matching(src, j).takeIf { it > 0 } ?: return null
        return src.substring(parenIdx, end + 1)
    }

    private fun lineOf(src: String, idx: Int) = src.substring(0, idx).count { it == '\n' } + 1

    private fun offenders(files: List<File>) = files.filter { it.extension == "kt" }.flatMap { f ->
        val src = f.readText(); val rel = f.path.substringAfter("kotlin/").substringAfter("java/")
        (unlabelledIconButtons(rel, src) + doubleToggleNodes(rel, src))
    }.filter { o -> allow.none { o.startsWith(it.substringBefore(':')) && o.endsWith(":" + it.substringAfter(':')) } }

    @Test fun everyIconOnlyControlIsLabelled() = assertEquals(emptyList<String>(), offenders(roots.flatMap { it.walkTopDown().toList() }))
    @Test fun theScanSeesTheSources() = assertTrue(roots.all { it.isDirectory })
    @Test fun theGuardCanFail() = assertEquals(1, unlabelledIconButtons("p", "IconButton(onClick = {}) { Icon(x, contentDescription = null) }").size)
    @Test fun theGuardSeesAnIconWithNestedParentheses() = assertEquals(1, unlabelledIconButtons("p", "IconButton(onClick = {}) { Icon(if (a) X else Y, contentDescription = null) }").size)
    @Test fun aLabelledIconIsAccepted() = assertEquals(0, unlabelledIconButtons("p", "IconButton(onClick = {}) { Icon(if (a) X else Y, contentDescription = s.menu) }").size)
    @Test fun theDoubleToggleProbeCanFail() = assertEquals(1, doubleToggleNodes("p", "Row(Modifier.clickable {}) { Checkbox(checked = v, onCheckedChange = { x() }) }").size)
    @Test fun theDoubleToggleProbeAcceptsTheMergedForm() = assertEquals(0, doubleToggleNodes("p", "Row(Modifier.toggleable(v, onValueChange = {})) { Checkbox(checked = v, onCheckedChange = null) }").size)
}
