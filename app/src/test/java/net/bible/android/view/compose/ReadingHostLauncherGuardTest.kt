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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing T8b: `NavHostComposeActivity` is the Activity the app opens on.
 *
 * The flip is otherwise UNGUARDED — the measurement pass checked `NavHostRoutingGuardTest`,
 * `MenuSeamGuardTest`, `ClassicReadingViewRemovalGuardTest`, `CollaboratorTypeGuardTest` and
 * `ReadingChromePortDriftTest` and none of them trips on any of T8b's edits. These tests are all
 * that stands between a later edit and a silent revert to the classic host.
 *
 * Paths are relative to the `:app` module directory, the JVM working directory for `:app`'s unit
 * tests — the convention `ActivityResultDispatchGuardTest` and `SourceEncodingGuardTest` already use.
 */
class ReadingHostLauncherGuardTest {

    private val mainManifest = File("src/main/AndroidManifest.xml").readText()
    private val standardManifest = File("src/standard/AndroidManifest.xml").readText()

    /** EVERY flavour manifest, found by walking rather than listed — a new flavour is covered
     *  automatically, and `debug` is no longer skipped (T8b fix round 1, M5). */
    private val allManifests: List<File> = File("src").listFiles()
        .orEmpty()
        .map { File(it, "AndroidManifest.xml") }
        .filter { it.isFile }
        .sortedBy { it.path }

    private val sources = File("src/main/java").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    @Test fun theWalkActuallySeesSource() {
        // Anti-vacuity: an empty walk passes every scan below.
        assertTrue("the src/main/java walk found no Kotlin sources", sources.size > 100)
        assertTrue("src/main/AndroidManifest.xml was not read", mainManifest.length > 1000)
        assertTrue("src/standard/AndroidManifest.xml was not read", standardManifest.length > 500)
        assertEquals(
            "the manifest walk must see main, standard, discrete and debug: " +
                allManifests.map { it.path },
            4,
            allManifests.size,
        )
    }

    // --- step 1: nothing starts the classic Activity by Intent any more ---------------------------

    @Test fun noProductionSiteStillLaunchesMainBibleActivityByIntent() {
        val offenders = sources
            .filter { ClassicRemovalScan.codeLinesOf(it.path).contains("MainBibleActivity::class.java") }
            .map { it.name }
            .sorted()
        assertEquals(
            "T8b repoints every Intent target at the nav host. codeLinesOf strips comments, so a " +
                "kdoc that merely mentions the class neither satisfies nor breaks this guard.",
            emptyList<String>(),
            offenders,
        )
    }

    // --- step 5 test 2: the boot path, both halves ------------------------------------------------

    /**
     * Asserted on the BLOCK, not on the file: the `standard` manifest also mentions
     * `.StartupActivity` as the plain `<activity>` the main manifest declares, and a whole-file grep
     * would pass for the wrong reason.
     */
    @Test fun theLauncherAliasStillTargetsStartupActivity() {
        val alias = blockContaining(standardManifest, "android.intent.category.LAUNCHER", "<activity-alias", "</activity-alias>")
        assertTrue(
            "the standard flavour's LAUNCHER alias must still target .StartupActivity — T8b moves " +
                "the handoff INSIDE that Activity, it does not move the entry point. Block was:\n$alias",
            alias.contains("""android:targetActivity=".StartupActivity""""),
        )
    }

    /**
     * The other half. A whole-file grep for "NavHostComposeActivity" in `StartupActivity.kt` passes
     * for the wrong reason — the class is already imported and used elsewhere in that file — so this
     * reads the boot handoff's own function body.
     */
    @Test fun theBootHandoffBuildsANavHostIntentOnTheChosenRoute() {
        val code = ClassicRemovalScan.codeLinesOf("src/main/java/net/bible/android/view/activity/StartupActivity.kt")
        val body = functionBody(code, "internal fun bootHandoffIntent(")
        assertTrue(
            "the boot handoff must construct the nav host's intent on the route gate (a) chose. Body was:\n$body",
            body.contains("NavHostComposeActivity.intentFor(context, route)"),
        )
        assertTrue(
            "FLAG_ACTIVITY_MULTIPLE_TASK stays on the ACTION_VIEW arm -- a second live host is what R7b's host " +
                "tokens exist for",
            body.contains("Intent.FLAG_ACTIVITY_MULTIPLE_TASK"),
        )
        assertTrue(
            "gate (a) must choose the route with the one predicate",
            code.contains("startRouteForBoot(hasUsableBible())"),
        )
    }

    /**
     * The `discrete` flavour's launcher, the other half of M5. Its overlay only flips
     * `android:enabled` on the `Calculator` alias (`tools:replace`), so the alias that actually
     * carries LAUNCHER and names a target lives in the MAIN manifest — and its target must still be
     * `.StartupActivity` too.
     */
    @Test fun theDiscreteLauncherAliasAlsoStillTargetsStartupActivity() {
        val alias = blockContaining(
            mainManifest,
            """android:name="net.bible.android.view.activity.Calculator"""",
            "<activity-alias",
            "</activity-alias>",
        )
        assertTrue(
            "the Calculator alias must carry the LAUNCHER filter it is the discrete entry point " +
                "through. Block was:\n$alias",
            alias.contains("android.intent.category.LAUNCHER"),
        )
        assertTrue(
            "…and still target .StartupActivity. Block was:\n$alias",
            alias.contains("""android:targetActivity=".StartupActivity""""),
        )
        val discrete = File("src/discrete/AndroidManifest.xml").readText()
        assertTrue(
            "the discrete overlay is expected to only enable that alias; if it ever names a target " +
                "of its own, this guard is watching the wrong block",
            !discrete.contains("android:targetActivity"),
        )
    }

    // --- I1: no file reaches a reading-view member through the classic type -----------------------

    /**
     * T8b fix round 1, I1. `Dialogs.agentPermissionDialog` and `LinkControl.showAllOccurrences` both
     * asked `(x as? MainBibleActivity)?.<reading member>`, which is **always null** once
     * `NavHostComposeActivity` hosts the reading view — so the AI-agent permission prompt had
     * silently become a plain `AlertDialog` and Strong's "find all occurrences" had stopped
     * searching in place. Neither failed anything: a null cast is a legal fall-through.
     *
     * `CollaboratorTypeGuardTest` polices the same pattern but over a FIXED file list that contains
     * neither file. This scan asks the whole tree instead.
     */
    @Test fun noFileReachesTheReadingViewThroughAClassicTypeTest() {
        val offenders = sources
            .filter { file ->
                val code = ClassicRemovalScan.codeLinesOf(file.path)
                Regex("""\bas\??\s+MainBibleActivity\b|\bis\s+MainBibleActivity\b""").containsMatchIn(code)
            }
            .map { it.name }
            .sorted()
        assertEquals(
            "a cast to the classic Activity is always null now that NavHostComposeActivity is the " +
                "reading host, and it fails silently. Use ReadingHostActivity (and readingCommands " +
                "for anything not on that 12-member interface). CurrentActivityHolder's " +
                "filterIsInstance<MainBibleActivity>() is deliberately NOT this pattern: it counts " +
                "classic instances for classic's own freeze().",
            emptyList<String>(),
            offenders,
        )
    }

    // --- step 3: the parentActivityName attributes ------------------------------------------------

    @Test fun noActivityDeclaresTheClassicActivityAsItsUpParent() {
        val offenders = allManifests
            .flatMap { f -> f.readLines().map { "${f.path}: ${it.trim()}" } }
            .filter { it.contains("parentActivityName") && it.contains("MainBibleActivity") }
        assertEquals(
            "after the flip MainBibleActivity is unreachable as a launcher; an Up affordance or a " +
                "TaskStackBuilder must not synthesise a back stack rooted in it",
            emptyList<String>(),
            offenders,
        )
    }

    @Test fun theNavHostIsNotItsOwnUpParent() {
        val block = blockContaining(
            mainManifest,
            """android:name="net.bible.android.view.activity.nav.NavHostComposeActivity"""",
            "<activity",
            "/>",
        )
        assertFalse(
            "the app's main screen has no Up parent — naming itself would be a cycle. Block was:\n$block",
            block.contains("parentActivityName"),
        )
    }

    // --- step 0: the chooser results reach whatever host opened them -------------------------------

    @Test fun everyGeneralBookKeyChooserArmAwaitsItsOwnResult() {
        val code = ClassicRemovalScan.codeLinesOf(
            "src/main/java/net/bible/android/control/page/CurrentGeneralBookPage.kt"
        )
        assertFalse(
            "startActivityForResult(…, STD_REQUEST_CODE) is answered only by " +
                "MainBibleActivity.onActivityResult, so on every other ActivityBase the user's " +
                "selection is discarded in silence — awaitIntent is resolved by ActivityBase itself",
            code.contains("startActivityForResult"),
        )
        assertEquals(
            "two awaitIntent call sites: the StudyPad arm's own, and awaitChosenKey's",
            2,
            Regex("awaitIntent\\(").findAll(code).count(),
        )
        assertEquals(
            "the other three arms — multi-document, my-document and the general-book key list — " +
                "each await through awaitChosenKey (3 calls plus its own declaration)",
            4,
            Regex("awaitChosenKey\\(").findAll(code).count(),
        )
        assertTrue(
            "the awaited answer must be applied through the shared applier",
            code.contains("KeyChooserResults.apply("),
        )
    }

    @Test fun theClassicDispatcherAndTheAwaitedArmsShareOneImplementation() {
        val mainBible = ClassicRemovalScan.codeLinesOf(
            "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"
        )
        assertTrue(
            "MainBibleActivity's surviving GenBookKey arm must read the extras through " +
                "KeyChooserResults, or the two readings of the same result can drift apart",
            mainBible.contains("KeyChooserResults.genBookKeyFrom(extras)"),
        )
        val readingCommands = ClassicRemovalScan.codeLinesOf(
            "src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt"
        )
        // T8c moved the two callers of `openMyDocumentPage` — the `MyDocumentPages` and
        // `MyDocuments` activity-result arms — out of the Activity and into `ReadingCommands`,
        // because on the reading host those results now arrive in-graph and never reach an
        // `onActivityResult` at all. The Activity's private wrapper went with them, so this
        // assertion FOLLOWS the delegation to its new file rather than being deleted: what it
        // protects is unchanged, that the key-map rebuild-and-retry has exactly one implementation.
        assertTrue(
            "ReadingCommands' my-document arms must delegate — the key-map rebuild-and-retry only " +
                "fires on a stale key map, so a second copy could lose it and nothing would notice",
            readingCommands.contains("KeyChooserResults.openMyDocumentPage("),
        )
        assertFalse(
            "MainBibleActivity must not grow a second openMyDocumentPage implementation beside it",
            mainBible.contains("MyDocumentBookManager.refreshDocument("),
        )
        assertTrue(
            "ReadingCommands.applyChosenDocument must delegate — the FakeBookFactory fallback only " +
                "matters for pseudo-documents, so a second copy could lose it silently",
            readingCommands.contains("KeyChooserResults.applyChosenDocument("),
        )
    }

    // --- steps 2 and 4: the start route survives onNewIntent's setIntent ---------------------------

    @Test fun theNavHostResolvesItsStartRouteThroughTheSavedStateAwarePath() {
        val code = ClassicRemovalScan.codeLinesOf(
            "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"
        )
        assertFalse(
            "requireNotNull(EXTRA_ROUTE) crashes on the bare Intent an Up affordance synthesises " +
                "for a parentActivityName target, which this host now is for seven Activities",
            code.contains("requireNotNull(intent.getStringExtra(EXTRA_ROUTE))"),
        )
        assertTrue(
            "onCreate must resolve its start route through navHostStartRoute",
            code.contains("navHostStartRoute("),
        )
        assertTrue(
            "onSaveInstanceState must pin the start route, or onNewIntent's setIntent(childRoute) " +
                "makes a later recreate() start this host on the child route",
            code.contains("outState.putString(STATE_START_ROUTE, route)"),
        )
        assertTrue(
            "a host that has bootstrapped owes a reading view for its whole life, so its recreate " +
                "must come back on NavRoutes.READING whatever it was created with",
            code.contains("if (readingAppBootstrapped) NavRoutes.READING else startRoute"),
        )
    }

    // --- helpers ----------------------------------------------------------------------------------

    /** The smallest [open]…[close] block of [text] that contains [needle]. */
    private fun blockContaining(text: String, needle: String, open: String, close: String): String {
        val at = text.indexOf(needle)
        assertTrue("'$needle' not found at all", at >= 0)
        val start = text.lastIndexOf(open, at)
        assertTrue("no '$open' before '$needle'", start >= 0)
        val end = text.indexOf(close, at)
        assertTrue("no '$close' after '$needle'", end >= 0)
        return text.substring(start, end + close.length)
    }

    /** [signature]'s body, by brace counting from the first `{` after it. */
    private fun functionBody(code: String, signature: String): String {
        val at = code.indexOf(signature)
        assertTrue("'$signature' not found", at >= 0)
        var i = code.indexOf('{', at)
        assertTrue("no body for '$signature'", i >= 0)
        var depth = 0
        val start = i
        while (i < code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return code.substring(start, i + 1) }
            }
            i++
        }
        throw AssertionError("unbalanced braces after '$signature'")
    }
}
