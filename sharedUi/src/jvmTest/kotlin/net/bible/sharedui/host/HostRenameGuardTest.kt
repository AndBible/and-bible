/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.host

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression net for the host rename (`Ios*` -> `Host*`, map in `scripts/host-rename-map.tsv`).
 *
 * Why: the rename is scripted and was applied at one point in time; code written in parallel (for example the
 * desktop `jvmMain` host) can still use the old `Ios*` names after a merge. This guard scans the Kotlin sources of
 * `hostMain`, `jvmMain` and `jvmTest` in `:sharedCore`, `:sharedLogic` and `:sharedUi` for any old name as a whole
 * word, and for files whose name stem is an old name. Comments are scanned (the rename script renames them too);
 * string literals are not (they may legitimately hold persisted ids or log text).
 *
 * String stripping is deliberately conservative and does not look inside templates: a whole literal (raw, regular
 * with backslash escapes, or char) is blanked, including any `${...}` code in it. Quotes inside comments are left
 * alone, so an apostrophe in a comment cannot swallow code.
 */
class HostRenameGuardTest {
    private val guardFileName = "HostRenameGuardTest.kt"
    private val scopedModules = listOf("sharedCore", "sharedLogic", "sharedUi")
    private val scopedSourceSets = listOf("hostMain", "jvmMain", "jvmTest")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "scripts/host-rename-map.tsv").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "scripts/host-rename-map.tsv not found above user.dir" }
    }

    private fun readMap(root: File): Map<String, String> =
        File(root, "scripts/host-rename-map.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val cols = line.split('\t')
                cols[0] to cols.getOrElse(1) { "?" }
            }

    private fun scopedFiles(root: File): List<File> =
        scopedModules.flatMap { module ->
            scopedSourceSets.map { File(root, "$module/src/$it") }
        }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { it.name != guardFileName }

    @Test
    fun noOldIosHostNamesRemain() {
        val root = repoRoot()
        val map = readMap(root)
        val files = scopedFiles(root)
        assertTrue(map.size > 50, "rename map looks empty or moved: ${map.size} names")
        val hostMainCount = files.count { "/src/hostMain/" in it.invariantSeparatorsPath }
        assertTrue(hostMainCount > 50, "guard went blind: saw only $hostMainCount hostMain files")

        val violations = mutableListOf<String>()
        for (file in files) {
            val rel = file.relativeTo(root).invariantSeparatorsPath
            map[file.nameWithoutExtension]?.let { violations += "$rel:1: file name ${file.nameWithoutExtension} -> $it" }
            findOldNames(file.readText(), map).forEach { (line, old) -> violations += "$rel:$line: $old -> ${map.getValue(old)}" }
        }
        assertTrue(
            violations.isEmpty(),
            "Old Ios* host names found (host rename already applied). Run scripts/apply-host-rename.sh <paths> " +
                "on these files:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun matcherReportsWholeWordsInCodeOnly() {
        val map = mapOf("IosFoo" to "HostFoo")
        val src = listOf(
            "val a = IosFoo()",                 // line 1: reported
            "val b = \"IosFoo\"",               // line 2: in a string
            "val c = \"\"\"x IosFoo",           // line 3: raw string start
            "IosFoo y\"\"\"",                   // line 4: still in the raw string
            "val d = IosFooBar + MyIosFoo",     // line 5: longer words
            "val e = \"a\\\"IosFoo\" + IosFoo", // line 6: escaped quote, then real use
            "// IosFoo in a comment",           // line 7: comments are scanned
        ).joinToString("\n")
        assertEquals(listOf(1 to "IosFoo", 6 to "IosFoo", 7 to "IosFoo"), findOldNames(src, map))
    }

    /** Returns (1-based line, old name) for each whole-word use of a map key outside string literals. */
    internal fun findOldNames(text: String, map: Map<String, String>): List<Pair<Int, String>> {
        val names = map.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val word = Regex("(?<![A-Za-z0-9_])($names)(?![A-Za-z0-9_])")
        return stripStringLiterals(text).lines().flatMapIndexed { i, line ->
            word.findAll(line).map { (i + 1) to it.groupValues[1] }.distinct().toList()
        }
    }

    /** Blanks string and char literals (newlines kept so line numbers stay valid); comments are copied as-is. */
    internal fun stripStringLiterals(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        fun blank(c: Char) = out.append(if (c == '\n') '\n' else ' ')
        while (i < s.length) {
            when {
                s.startsWith("//", i) -> {
                    while (i < s.length && s[i] != '\n') out.append(s[i++])
                }
                s.startsWith("/*", i) -> {
                    val end = s.indexOf("*/", i + 2).let { if (it < 0) s.length else it + 2 }
                    out.append(s, i, end); i = end
                }
                s.startsWith("\"\"\"", i) -> {
                    val end = s.indexOf("\"\"\"", i + 3).let { if (it < 0) s.length else it + 3 }
                    for (k in i until end) blank(s[k])
                    i = end
                }
                s[i] == '"' -> {
                    blank(s[i++])
                    while (i < s.length && s[i] != '"' && s[i] != '\n') {
                        if (s[i] == '\\' && i + 1 < s.length && s[i + 1] != '\n') blank(s[i++])
                        blank(s[i++])
                    }
                    if (i < s.length && s[i] == '"') blank(s[i++])
                }
                s[i] == '\'' -> {
                    val m = Regex("'(?:\\\\[^'\\n]*|[^'\\\\\\n])'").matchAt(s, i)
                    if (m != null) { for (k in i until i + m.value.length) blank(s[k]); i += m.value.length }
                    else out.append(s[i++])
                }
                else -> out.append(s[i++])
            }
        }
        return out.toString()
    }
}
