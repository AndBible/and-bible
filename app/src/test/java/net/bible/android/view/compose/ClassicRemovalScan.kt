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

package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Shared scanning helpers for the Batch Z-late phase 1 removal guards, one per slice
 * (`ClassicSearchRemovalGuardTest`, `ClassicReadingPlanRemovalGuardTest`, …).
 *
 * Extracted in slice S2 rather than copied: the two subtleties below were each got wrong once
 * while S1 was being written, and there are ~17 slices left. Paths are relative to the `:app`
 * module dir, which is the working directory for its unit tests.
 */
object ClassicRemovalScan {
    /**
     * The manifests a slice may have to sweep, discovered by globbing rather than a static list —
     * a future flavor manifest (a new source set gaining its own `AndroidManifest.xml`) is picked
     * up automatically instead of being silently skipped. Not every variant declares one; callers
     * filter.
     */
    val manifestPaths: List<String>
        get() = (File("src").listFiles { f -> f.isDirectory } ?: emptyArray())
            .map { "src/${it.name}/AndroidManifest.xml" }
            .filter { File(it).isFile }
            .sorted()

    /**
     * Non-prose lines only: a comment must not satisfy or defeat a scan. `import` lines are
     * dropped too by default — for a "does this file still compare X" scan an import is noise —
     * but a fully-qualified-name sweep passes `keepImports = true`, because there an import IS
     * the reference being hunted, and with imports stripped such a sweep finds nothing at all
     * and passes vacuously.
     */
    fun codeLinesOf(path: String, keepImports: Boolean = false): String =
        File(path).readLines().filterNot { line ->
            val trimmed = line.trimStart()
            (!keepImports && trimmed.startsWith("import ")) || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")

    /**
     * Fully-qualified names as regexes with a TRAILING non-identifier boundary only — there is no
     * leading boundary. The trailing boundary is not decoration: a deleted `…search.EpubSearch`
     * must not match the surviving `…search.EpubSearchResultKey`, and a deleted
     * `…readingplan.DailyReading` must not match the surviving
     * `…readingplan.DailyReadingComposeActivity`. With a plain `contains`, a slice whose Compose
     * twins are named after their classic originals — which is all of them — has a sweep that can
     * never pass.
     *
     * One-sided on purpose, not an oversight. Every caller today passes a FULLY-QUALIFIED name, for
     * which the missing leading boundary is harmless in practice. A leading boundary such as
     * `(?<![A-Za-z0-9_.])` would be the right form for a FQN, but is fiddlier than it looks: it
     * would be actively WRONG the day a caller passes a bare (non-qualified) class name instead,
     * since a bare name is routinely preceded by a `.` (ordinary qualified access, e.g.
     * `readingplan.DailyReading`) — exactly the character that form of leading boundary rejects.
     * Left out rather than added speculatively; add a leading boundary only alongside a caller that
     * actually needs one, matched to what that caller passes.
     */
    fun refsFor(fqNames: List<String>): List<Regex> =
        fqNames.map { Regex(Regex.escape(it) + "(?![A-Za-z0-9_])") }

    /**
     * Every Kotlin/Java source under every SHIPPING source set (`src/main`, `src/debug`,
     * `src/standard`, `src/discrete`, …), for a whole-tree reference sweep — `src/test` and
     * `src/androidTest` are excluded, because they are test code, not app code: they cannot
     * themselves be launched or shipped, so a class name surviving only there is not the kind of
     * reintroduction this scan exists to catch, and test fixtures legitimately reference doomed
     * classes right up until the guard test for them is deleted. Originally `mainSources()` /
     * `src/main`-only, widened in slice S2's fix wave once a helper meant for ~17 slices was found
     * to encode "all classic code lives in `src/main`" as a silent premise; before widening, both
     * `ClassicSearchRemovalGuardTest` and this file's own guard were re-run to confirm the wider
     * scan does not newly catch a real reference in `src/debug`/`src/discrete`/`src/standard`.
     */
    fun appSources(): List<File> =
        File("src").listFiles { f -> f.isDirectory && f.name != "test" && f.name != "androidTest" }
            .orEmpty()
            .flatMap { sourceSet ->
                sourceSet.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            }

    /**
     * Every resource XML under every SHIPPING source set's `res/` tree (`src/main/res`,
     * `src/debug/res`, `src/discrete/res`, …), globbed rather than listed for the same reason
     * [manifestPaths] globs: a source set that gains a `res/` tree later is picked up instead of
     * being silently skipped (`src/standard` has none today). `src/test` and `src/androidTest` are
     * excluded exactly as in [appSources]; neither has a `res/` tree.
     *
     * The manifest is deliberately NOT here — it sits outside `res/` and has its own sweep,
     * [assertNoManifestNames]. Comments are NOT stripped (unlike [codeLinesOf], which strips KOTLIN
     * comment syntax and would be simply wrong here — XML comments are `<!--` with `~`-prefixed
     * continuation lines in this repo): a commented-out `<net.bible.…>` tag is still a dangling
     * reference to a deleted class, and flagging it is the safe direction.
     */
    fun appResourceXml(): List<File> =
        File("src").listFiles { f -> f.isDirectory && f.name != "test" && f.name != "androidTest" }
            .orEmpty()
            .map { File(it, "res") }
            .filter { it.isDirectory }
            .flatMap { res -> res.walkTopDown().filter { it.isFile && it.extension == "xml" } }

    /**
     * The five assertions every slice removal guard now makes, extracted in batch S4+S5+S7+S8 for
     * S4 onwards. S3's final review asked for this: the arm-scanning body had been copied into
     * three guards by then, and the third had to explain in KDoc why its copy diverged from the
     * other two. Extracting also fixes a defect that review found — in the copies, the
     * anti-vacuity precondition sat AFTER the assertion it protects, so an empty scan surfaced as
     * the wrong failure. Here the precondition runs first, by construction, and a later slice
     * cannot forget it.
     *
     * S1/S2/S3 kept their own inlined copies at first — retrofitting them was deferred as a change
     * to already-gated slices with no defect behind it, leaving spec 2.3's tail sweep to own it if
     * anyone wanted uniformity. The S6+S16+S18+S19 batch gave that retrofit a real defect: the
     * resource-XML gap fixed in [assertNoSourceNames] meant the three inlined copies were sweeping
     * with a hole the shared assertion had already closed, so they were retrofitted onto it there,
     * and all seven landed guards now share this one code path.
     *
     * `hint` is the message a failure prints. Write it for the person who broke the guard two
     * years from now, not for the person adding it: say what was deleted, and — where a slice
     * keeps something referenceless on purpose — say what is deliberately NOT in the list.
     */
    fun assertPathsGone(paths: List<String>, hint: String) {
        assertTrue(
            "cwd is not the :app module dir — this assertion would pass vacuously",
            File("src/main").isDirectory,
        )
        assertEquals(hint, emptyList<String>(), paths.filter { File(it).exists() }.sorted())
    }

    /**
     * The inverse of [assertPathsGone], for a slice that makes a file REFERENCELESS but keeps it
     * (spec 2.4). Such a file could be deleted with every gate still green — a compile passes, the
     * reference proof passes, no golden moves — so only an assertion of its presence defends it.
     * Needs no precondition of its own beyond the module-dir check: a wrong working directory
     * makes this assertion FAIL rather than pass, which is the safe direction.
     */
    fun assertPathsPresent(paths: List<String>, hint: String) {
        assertTrue(
            "cwd is not the :app module dir — this assertion would report every path as missing",
            File("src/main").isDirectory,
        )
        assertEquals(hint, emptyList<String>(), paths.filterNot { File(it).exists() }.sorted())
    }

    /**
     * Walks every shipping source set rather than a path list — both its Kotlin/Java sources
     * ([appSources]) and its resource XML ([appResourceXml]) — so a class named by its
     * FULLY-QUALIFIED name from outside the deleted package cannot escape. An unqualified
     * same-package reference is invisible to this sweep — and does not need it, because the
     * compiler already catches that: the class it would resolve to is gone.
     *
     * The resource arm was added in the S6+S16+S18+S19 batch, after batch S4+S5+S7+S8's final
     * review found the KDoc here claimed a fully-qualified name "cannot escape" while the walk
     * opened only `.kt`/`.java`. **Layout XML is exactly where this repo names classes
     * fully-qualified**: eight layouts carry a bare `<net.bible.…>` element tag, `res/xml/settings.xml`
     * names a custom `Preference` class three times, and `CalendarHeatmapView`'s only two
     * instantiation sites in the whole tree were `reading_progress.xml:191` and `:485`. Such a
     * reference is resolved by `LayoutInflater` at RUNTIME: the compiler, the unit suite and
     * Roborazzi all miss it alike and `assembleStandardGithubDebug` packages it happily, so a
     * regression surfaces only as an `InflateException` on a user's device.
     *
     * The two arms differ deliberately. Source: imports are KEPT ([codeLinesOf]
     * `keepImports = true`) because for a fully-qualified sweep an import IS the reference being
     * hunted, and Kotlin comment lines are dropped. Resources: nothing is stripped, and offenders
     * are reported per LINE (as in [assertNoManifestNames]) because a layout is long and the
     * offending tag is what has to go — removing the TAG, not the class, is the fix. Each arm
     * carries its own vacuity precondition: a single combined count would let a broken source walk
     * hide behind ~470 resource files.
     *
     * Callers MUST pass fully-qualified names. A bare class name against resource XML is not merely
     * loose, it is unusable: `Search` matches ~140 lines of `strings.xml` under `values` locale
     * directories in this repo alone. See [refsFor] for why the boundary is trailing-only.
     */
    fun assertNoSourceNames(fqNames: List<String>, hint: String) {
        val refs = refsFor(fqNames)
        val sources = appSources()
        assertTrue("the source-set walk found no Kotlin/Java source at all", sources.size > 100)
        val resources = appResourceXml()
        assertTrue("the res/ walk found no resource XML at all", resources.size > 100)
        val sourceOffenders = sources
            .filter { file ->
                val code = codeLinesOf(file.path, keepImports = true)
                refs.any { it.containsMatchIn(code) }
            }
            .map { it.path.replace('\\', '/') }
        val resourceOffenders = resources.flatMap { file ->
            val path = file.path.replace('\\', '/')
            file.readLines().mapIndexedNotNull { index, line ->
                if (refs.any { it.containsMatchIn(line) }) "$path:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(hint, emptyList<String>(), (sourceOffenders + resourceOffenders).sorted())
    }

    /**
     * No manifest may declare, or point at, a class a slice deletes. Covers `android:name` and
     * `android:parentActivityName` alike, which is the point: `parentActivityName` is a plain
     * string consumed by `NavUtils` at runtime, so nothing compiles against it, no other test
     * reads it and no golden renders it. S2 found a surviving Compose activity parented to a
     * doomed classic class with zero automated signal, and batch S4+S5+S7+S8 found three more.
     */
    fun assertNoManifestNames(fqNames: List<String>, hint: String) {
        assertTrue(
            "src/main/AndroidManifest.xml is missing — this assertion would pass vacuously",
            File("src/main/AndroidManifest.xml").isFile,
        )
        val refs = refsFor(fqNames)
        val offenders = manifestPaths
            .filter { File(it).isFile }
            .flatMap { path ->
                File(path).readLines()
                    .filter { line -> refs.any { it.containsMatchIn(line) } }
                    .map { "$path: ${it.trim()}" }
            }
            .sorted()
        assertEquals(hint, emptyList<String>(), offenders)
    }

    /**
     * A collapsed arm is `Screen.X -> XComposeActivity::class.java`, with no flag branch left in
     * it. Takes the text from each arm's `Screen.X ->` up to the next `Screen.` and asserts no
     * branch survives inside it; a missing arm counts as an offender, so a deleted enum entry
     * cannot pass silently. The literal `" ->"` in the search string keeps an arm from matching a
     * longer-named sibling (`Screen.MyDocuments` vs `Screen.MyDocumentPages`).
     *
     * `else` is matched as a WHOLE WORD, not as the literal `"else "` the S1 and S2 copies used —
     * S2's review flagged that an `else` at end of line slips past the literal form, and S3 made
     * the fix before the pattern was copied further.
     *
     * Known bound, stated precisely rather than overclaimed: the arm ends at the next `Screen.`
     * TOKEN, which is not the same as the next `when` arm. An arm whose own body mentioned
     * `Screen.` — say `Screen.X -> if (useComposeFor(Screen.X))` — would be truncated before its
     * branch detector fired. No collapsed arm can take that shape, and every branching arm in the
     * file today writes `useComposeFor(screen)` with the implicit parameter, so the bound is exact
     * in practice; it is documented because ~11 more slices will rely on it.
     */
    fun assertLauncherArmsUnconditional(screens: List<String>, hint: String) =
        assertLauncherArmsUnconditionalIn(LAUNCHER_PATH, screens, hint)

    /** `ScreenLauncher.kt`, relative to the `:app` module dir — what the slice guards scan. */
    const val LAUNCHER_PATH = "src/main/java/net/bible/android/view/ScreenLauncher.kt"

    /**
     * [assertLauncherArmsUnconditional] against an arbitrary file. `internal` and NOT part of the
     * helper's slice-guard surface: every slice guard scans the real [LAUNCHER_PATH] and must keep
     * doing so. The one caller is `ClassicRemovalScanAssertionsTest`, which since slice S12 has no
     * still-branching arm left in the real file to use as its deliberately-failing input, and so
     * hands this a hand-written fixture instead. Making the path a parameter rather than
     * reimplementing the scan in the test is the point: the fixture must exercise THIS code, or the
     * test proves nothing about what the slice guards run.
     */
    internal fun assertLauncherArmsUnconditionalIn(path: String, screens: List<String>, hint: String) {
        assertTrue("$path is missing — this assertion would pass vacuously", File(path).isFile)
        val code = codeLinesOf(path)
        assertTrue(
            "$path no longer reads use_compose_ui at all — the flag must survive until the " +
                "epilogue (spec 3.4)",
            code.contains("useComposeFor"),
        )
        val elseWord = Regex("""\belse\b""")
        val offenders = screens.filter { screen ->
            val start = code.indexOf("$screen ->")
            if (start < 0) return@filter true
            val next = code.indexOf("Screen.", start + screen.length + 3)
            val arm = if (next < 0) code.substring(start) else code.substring(start, next)
            arm.contains("useComposeFor") || elseWord.containsMatchIn(arm)
        }
        assertEquals(hint, emptyList<String>(), offenders)
    }
}
