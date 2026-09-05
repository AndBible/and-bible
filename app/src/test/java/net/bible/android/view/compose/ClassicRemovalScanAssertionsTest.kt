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
        // BookmarkListItem is inflated by name from two layouts (bookmark_list_item.xml:19,
        // studypad_list_item.xml:19) and is written UNQUALIFIED everywhere in Kotlin — view binding
        // does the rest — so its fully-qualified name appears in NO shipping .kt/.java file. Both
        // premises are asserted before use: if either stops holding, this test would pass for the
        // wrong reason, which is the exact vacuity it exists to prevent.
        val fq = "net.bible.android.view.util.widget.BookmarkListItem"
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
                "two layouts still inflate this class by name, so the helper must fail",
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

    @Test fun assertLauncherArmsUnconditionalFailsOnAnArmThatStillBranches() {
        // Screen.Settings is S12's, which is BLOCKED on spec 11.4 and therefore still branches on
        // the flag today. If S12 ever lands, this input stops being a branching arm and this half
        // of the test would pass for the wrong reason — so assert the premise first.
        val code = ClassicRemovalScan.codeLinesOf("src/main/java/net/bible/android/view/ScreenLauncher.kt")
        val start = code.indexOf("Screen.Settings ->")
        assertTrue("Screen.Settings is missing from ScreenLauncher — pick another branching arm", start >= 0)
        val next = code.indexOf("Screen.", start + "Screen.Settings".length + 3)
        assertTrue(
            "Screen.Settings no longer branches on the flag — S12 must have landed; " +
                "pick another still-branching arm for this test's failing input",
            code.substring(start, next).contains("useComposeFor"),
        )
        assertThrows(AssertionError::class.java) {
            ClassicRemovalScan.assertLauncherArmsUnconditional(
                listOf("Screen.Settings"),
                "this arm still branches on the flag, so the helper must fail",
            )
        }
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.ChooseMapKey"),
            "S3 collapsed this arm, so the helper must pass",
        )
    }
}
