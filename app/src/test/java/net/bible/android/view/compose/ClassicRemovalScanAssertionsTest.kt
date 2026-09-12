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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the assertion helpers [ClassicRemovalScan] gained in batch S4+S5+S7+S8, in BOTH
 * directions. A guard test is only worth its run time if it fails when the thing it guards
 * regresses, and these five helpers are now the whole failure mechanism of every slice guard from
 * S4 onwards — so each one is driven once with an input that must fail and once with an input that
 * must pass.
 *
 * The clean-direction inputs are deliberately real paths in this repo rather than fixtures: the
 * helpers resolve paths relative to the `:app` module dir (the working directory for its unit
 * tests), so a fixture in a temp directory would exercise a different code path than production
 * use and would not prove the preconditions work.
 *
 * ONE exception, forced by slice S12: the launcher-arm test's FAILING input is now a synthetic
 * fixture, because S12 collapsed the last arm in `ScreenLauncher.kt` that still branched on the
 * flag and there is no real branching arm left to hand it. Its clean-direction inputs are still
 * real, and its fixture is scanned by the same helper body the slice guards run — see that test's
 * own kdoc.
 */
class ClassicRemovalScanAssertionsTest {
    @Test fun assertPathsGoneFailsWhenAPathSurvives() {
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertPathsGone(
                listOf("src/main/AndroidManifest.xml"),
                "this path exists, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertPathsGone(
            listOf("src/main/java/net/bible/android/view/activity/navigation/NoSuchFileEver.kt"),
            "this path does not exist, so the helper must pass",
        )
    }

    @Test fun assertPathsPresentFailsWhenAPathIsMissing() {
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertPathsPresent(
                listOf("src/main/java/net/bible/android/view/activity/navigation/NoSuchFileEver.kt"),
                "this path does not exist, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/AndroidManifest.xml"),
            "this path exists, so the helper must pass",
        )
    }

    @Test fun assertNoSourceNamesFailsOnAClassThatIsStillReferenced() {
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertNoSourceNames(
                listOf("net.bible.android.view.activity.page.MainBibleActivity"),
                "MainBibleActivity is referenced all over the tree, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertNoSourceNames(
            listOf("net.bible.android.view.activity.navigation.NoSuchClassEver"),
            "nothing names this class, so the helper must pass",
        )
    }

    /**
     * The resource arm, added in this batch. Batch S4+S5+S7+S8's final review found the sweep
     * walked only `.kt`/`.java` while its KDoc claimed a fully-qualified name "cannot escape" it —
     * and layout XML is exactly where this repo names classes fully-qualified. Such a reference is
     * resolved by `LayoutInflater` at RUNTIME, so nothing else in the gate can see it.
     */
    @Test fun assertNoSourceNamesFailsOnAClassOnlyALayoutNames() {
        // SwitchCompat is inflated by name from ONE surviving layout (share_verses.xml, twelve
        // times) and is written UNQUALIFIED nowhere in Kotlin either -- ShareWidget.kt goes
        // through view binding -- so its fully-qualified name appears in NO shipping .kt/.java
        // file. Both premises are asserted before use: if either stops holding, this test would
        // pass for the wrong reason, which is the exact vacuity it exists to prevent.
        //
        // The fixture used to be net.bible.android.view.util.widget.BookmarkListItem, named by
        // studypad_list_item.xml. The epilogue deleted that layout, and with it the LAST resource
        // XML in the tree naming any net.bible class fully-qualified -- settings.xml's three
        // InverseMultiSelectListPreference tags went in the same commit. So there is no net.bible
        // candidate left at all, and the fixture is a third-party view class instead. The helper
        // does not care about the package: what it needs is a class that resource XML names and
        // source does not.
        val fq = "androidx.appcompat.widget.SwitchCompat"
        val ref = ClassicRemovalScan.refsFor(listOf(fq)).single()
        val namedInSource = ClassicRemovalScan.appSources()
            .filter { ref.containsMatchIn(ClassicRemovalScan.codeLinesOf(it.path, keepImports = true)) }
            .map { it.path }
        assertTrue(
            "$fq is now named fully-qualified in $namedInSource, so the SOURCE arm would supply " +
                "the failure and this test would no longer prove the resource arm works — pick " +
                "another class named only by a layout",
            namedInSource.isEmpty(),
        )
        assertTrue(
            "no resource XML names $fq any more — pick another class from a surviving layout",
            ClassicRemovalScan.appResourceXml().any { f -> f.readLines().any { ref.containsMatchIn(it) } },
        )
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertNoSourceNames(
                listOf(fq),
                "one layout still inflates this class by name, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertNoSourceNames(
            listOf("net.bible.android.view.util.widget.NoSuchWidgetEver"),
            "neither a source file nor a layout names this class, so the helper must pass",
        )
    }

    @Test fun assertNoManifestNamesFailsOnAClassTheManifestDeclares() {
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertNoManifestNames(
                listOf("net.bible.android.view.activity.page.MainBibleActivity"),
                "the manifest declares MainBibleActivity, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertNoManifestNames(
            listOf("net.bible.android.view.activity.navigation.NoSuchClassEver"),
            "no manifest names this class, so the helper must pass",
        )
    }

    /**
     * The failing input is a SYNTHETIC fixture, not a real arm. Every other test in this class
     * drives its helper with real repo paths on purpose (see the class kdoc), and this one used
     * `Screen.Settings` — the last arm that still branched on the flag — right up to slice S12.
     * S12 collapsed it, and with it the last branching arm anywhere in `ScreenLauncher.kt`, so
     * there is nothing real left to point this half at.
     *
     * The fixture is written to a temp file and scanned by
     * [ClassicRemovalScan.assertLauncherArmsUnconditionalIn], the same body
     * [ClassicRemovalScan.assertLauncherArmsUnconditional] runs — only the path is a parameter. So
     * this still exercises the real detector rather than a re-implementation of it. The fixture
     * carries THREE arms: one that branches on the flag (must fail), one that is collapsed (must
     * pass), and one that branches WITHOUT naming the flag (must fail, and can only be caught by
     * the `else` clause — see the comment on it below). The clean-direction call against the real
     * file below keeps the production path covered too.
     */
    @Test fun assertLauncherArmsUnconditionalFailsOnAnArmThatStillBranches() {
        val fixture = File.createTempFile("ScreenLauncherFixture", ".kt").apply { deleteOnExit() }
        fixture.writeText(
            """
            object ScreenLauncher {
                fun useComposeFor(screen: Screen): Boolean = flag
                fun targetFor(screen: Screen): Class<*> = when (screen) {
                    Screen.StillBranching ->
                        if (useComposeFor(screen)) NewActivity::class.java
                        else OldActivity::class.java
                    Screen.AlreadyCollapsed -> NewActivity::class.java
                    Screen.BranchesWithoutTheFlag ->
                        if (someRuntimeCondition) NewActivity::class.java
                        else OldActivity::class.java
                }
            }
            """.trimIndent(),
        )

        // Premise: the fixture is what a branching arm and a collapsed arm actually look like in
        // ScreenLauncher.kt. If the real file's shape ever diverges from this, the fixture stops
        // standing in for it — which is the one way this synthetic input could rot.
        val real = ClassicRemovalScan.codeLinesOf(ClassicRemovalScan.LAUNCHER_PATH)
        assertTrue(
            "ScreenLauncher.kt no longer writes collapsed arms as `Screen.X -> XComposeActivity::class.java`" +
                " — the fixture below no longer resembles the file this helper scans",
            // Matched as a SHAPE, not as one named screen. This premise has now been repointed
            // twice by unrelated migrations (it named Screen.Settings until nav-graph 3/5/6
            // Task 9 moved that arm onto the throwing helper, then Screen.Bookmarks, which the
            // same migration will eventually move too), and every named witness is scheduled to
            // rot the same way while the SHAPE it stands for does not: any one surviving
            // collapsed arm proves the fixture still resembles the real file, and when the LAST
            // one goes this assertion is SUPPOSED to fail, because the fixture really will have
            // stopped standing in for anything.
            Regex("""Screen\.\w+ -> \w+ComposeActivity::class\.java""").containsMatchIn(real),
        )

        // Anti-vacuity: a MISSING arm also counts as an offender (asserted below), so without this
        // the branching case could be passing for the wrong reason -- the helper failing because it
        // never found the arm at all rather than because it saw the branch.
        assertTrue(
            "the fixture's branching arm is not written in the shape the scan looks for",
            ClassicRemovalScan.codeLinesOf(fixture.path).contains("Screen.StillBranching ->"),
        )
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertLauncherArmsUnconditionalIn(
                fixture.path,
                listOf("Screen.StillBranching"),
                "this arm still branches on the flag, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertLauncherArmsUnconditionalIn(
            fixture.path,
            listOf("Screen.AlreadyCollapsed"),
            "this arm is collapsed, so the helper must pass",
        )

        // The clause that is actually LIVE in production, exercised ALONE. Task 6 retired this
        // helper's flag precondition on the argument that the detector looks for `useComposeFor`
        // OR an `else`; Task 7 then deleted the flag, and `FlagRemovalGuardTest` now asserts
        // `useComposeFor` can never appear in a production source at all. So `\belse\b` is the
        // whole live detector — but the branching arm above satisfies BOTH clauses, so this test
        // would keep passing with the `else` regex broken (verified by injection in the epilogue's
        // fix wave). This arm branches with no flag token in it, so only the `else` clause can
        // catch it.
        val fixtureCode = ClassicRemovalScan.codeLinesOf(fixture.path)
        val flaglessArmStart = fixtureCode.indexOf("Screen.BranchesWithoutTheFlag ->")
        assertTrue(
            "the flagless arm is not written in the shape the scan looks for",
            flaglessArmStart >= 0,
        )
        assertTrue(
            "the flagless arm must contain no flag token, or it would satisfy the other clause too " +
                "and prove nothing about `else`. It is written LAST in the fixture on purpose: the " +
                "helper's arm slice runs to the next `Screen.` token or, for the last arm, to the " +
                "end of the file — so this substring IS the text the helper scans, not a re-derived " +
                "approximation of it",
            !fixtureCode.substring(flaglessArmStart).contains("useComposeFor"),
        )
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertLauncherArmsUnconditionalIn(
                fixture.path,
                listOf("Screen.BranchesWithoutTheFlag"),
                "this arm branches without naming the flag, so the helper must fail",
            )
        }
        // A MISSING arm counts as an offender too — that is what stops a deleted enum entry from
        // passing silently, and nothing else in this class covers it.
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertLauncherArmsUnconditionalIn(
                fixture.path,
                listOf("Screen.NotInTheFileAtAll"),
                "this arm does not exist, so the helper must fail",
            )
        }

        // And the production path itself, against the real file.
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.ChooseMapKey"),
            "S3 collapsed this arm, so the helper must pass",
        )
    }
}
