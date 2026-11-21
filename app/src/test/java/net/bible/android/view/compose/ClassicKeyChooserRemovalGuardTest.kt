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
import org.junit.Test

/**
 * Batch Z-late phase 1, slice S3: the three classic key-chooser screens were deleted and their
 * `ScreenLauncher` arms collapsed to the Compose implementations. This guard makes the deletion
 * durable — a regression that re-adds a file, an import, a manifest entry or a flag branch would
 * otherwise only be noticed if something else broke.
 *
 * It also does something the S1 and S2 guards did not have to: it asserts that a file which
 * becomes REFERENCELESS in this slice is still present. `ChooseGeneralBookKey` and `ChooseMapKey`
 * were the only two subclasses of `ChooseKeyBase`, so once they went, the base, its adapter and
 * its layout had no consumer at all — and spec §2.4 kept them for a time anyway, as a tail-sweep
 * item, together with `ListActivityBase` and `DocumentSelectionBase`. nav-graph slice 8 F7 was
 * that tail sweep for `ChooseKeyBase` and `choose_general_book_key.xml` (zero subclasses even
 * after F4/F6): see [theTailSweptFilesAreGone]. `KeyItemAdapter.kt` was swept later (#4171),
 * together with the classic `util/buttongrid` grid chooser; see [theViewEraResidueIsGone].
 *
 * Source scan rather than a reflective "class not found", matching [ClassicSearchRemovalGuardTest]
 * and [ClassicReadingPlanRemovalGuardTest] and sharing their machinery via [ClassicRemovalScan].
 * Paths are relative to the `:app` module dir, which is the working directory for its unit tests.
 *
 * The phase-wide guard spec §6 describes (iterate `Screen.entries` and assert every arm resolves
 * into the Compose set) cannot be true until the last slice lands, so it is deliberately not
 * attempted here.
 */
class ClassicKeyChooserRemovalGuardTest {
    /** Fully-qualified names of the three screens S3 deletes. `ChooseKeyBase` is NOT one of them. */
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey",
        "net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey",
        "net.bible.android.view.activity.navigation.ChooseDictionaryWord",
    )

    private val doomedClassRefs = ClassicRemovalScan.refsFor(doomedClassNames)

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/navigation/genbookmap/ChooseGeneralBookKey.kt",
        "src/main/java/net/bible/android/view/activity/navigation/genbookmap/ChooseMapKey.kt",
        "src/main/java/net/bible/android/view/activity/navigation/ChooseDictionaryWord.kt",
        "src/main/res/layout/choose_dictionary_page.xml",
    )

    @Test fun theClassicKeyChooserFilesAndResourcesAreGone() {
        assertTrue(
            "cwd is not the :app module dir — this guard would pass vacuously",
            File("src/main").isDirectory,
        )
        val survivors = doomedPaths.filter { File(it).exists() }
        assertEquals(
            "these classic key-chooser files or resources should have been deleted in S3. Note " +
                "see also theTailSweptFilesAreGone and theViewEraResidueIsGone for later sweeps.",
            emptyList<String>(),
            survivors,
        )
    }

    /**
     * `KeyChooserKeys.kt` sits in the same doomed-looking package and is named after the deleted
     * feature, but it is read by surviving Compose activities and by the reading view's quick-sheet,
     * so deleting it breaks shipping code.
     */
    @Test fun theSurvivingKeyChooserCollaboratorsStillExist() = ClassicRemovalScan.assertPathsPresent(
        listOf("../sharedUi/src/commonMain/kotlin/net/bible/android/view/activity/navigation/genbookmap/KeyChooserKeys.kt"),
        "KeyChooserKeys.kt is read by the surviving Compose activities and ComposeReadingViewHost's quick-sheet",
    )

    /**
     * #4171: View-era code without callers since the Compose port: the classic grid chooser
     * (`util/buttongrid`), `KeyItemAdapter` and the unused `frozen`, `empty`, `nav_header_main` and
     * `buttongrid_button_preview` layouts must stay gone.
     */
    @Test fun theViewEraResidueIsGone() = ClassicRemovalScan.assertPathsGone(
        listOf(
            "src/main/java/net/bible/android/view/util/buttongrid",
            "src/main/java/net/bible/android/view/activity/navigation/genbookmap/KeyItemAdapter.kt",
            "src/main/res/layout/frozen.xml",
            "src/main/res/layout/empty.xml",
            "src/main/res/layout/nav_header_main.xml",
            "src/main/res/layout/buttongrid_button_preview.xml",
            "src/main/res/drawable-mdpi/buttongrid_button_feedback_background.9.png",
        ),
        "#4171: View-era code with no callers since the Compose port",
    )

    @Test fun theRemovedViewEraClassesAreNotNamedAgain() = ClassicRemovalScan.assertNoSourceNames(
        listOf(
            "net.bible.android.view.util.buttongrid.ButtonGrid",
            "net.bible.android.view.util.buttongrid.LayoutDesigner",
            "net.bible.android.view.util.buttongrid.OnButtonGridActionListener",
            "net.bible.android.view.util.buttongrid.BottomBarProgressDrawable",
            "net.bible.android.view.activity.navigation.genbookmap.KeyItemAdapter",
        ),
        "#4171: a removed View-era class is referenced again",
    )

    /**
     * nav-graph slice 8 F7: `ChooseKeyBase` lost its last subclass (both `ChooseGeneralBookKey`
     * and `ChooseMapKey` already gone, since S3) and is deleted, along with the layout only it
     * inflated. Moved out of [theSurvivingKeyChooserCollaboratorsStillExist] above, which used to
     * pin both PRESENT.
     */
    @Test fun theTailSweptFilesAreGone() = ClassicRemovalScan.assertPathsGone(
        listOf(
            "src/main/java/net/bible/android/view/activity/navigation/genbookmap/ChooseKeyBase.kt",
            "src/main/res/layout/choose_general_book_key.xml",
        ),
        "slice 8 F7: zero subclasses; spec §2 deletes the family's dead members",
    )

    /**
     * The reference proof of spec §3.3, expressed as a test so it survives this session. Delegates
     * to [ClassicRemovalScan.assertNoSourceNames], which walks every shipping source set's
     * Kotlin/Java source AND its resource XML rather than a path list, so a new reference to a
     * deleted class by its FULLY-QUALIFIED name from OUTSIDE the deleted package — whether written
     * in source or named by a layout — cannot escape. That scoping matters: an unqualified
     * same-package reference (a bare `ChooseMapKey` written inside `genbookmap/`) is invisible to
     * this sweep — but that case does not need this test, because the compiler already catches it:
     * the class it would resolve to no longer exists.
     *
     * Matching on the FULLY-QUALIFIED name with a trailing (not leading — see
     * [ClassicRemovalScan.refsFor]) non-identifier boundary is what makes this possible at all:
     * every surviving Compose twin is named after its classic original (`ChooseMapKey` /
     * `ChooseMapKeyComposeActivity`), and imports are KEPT because an import is the reference being
     * hunted — see [ClassicRemovalScan] for why both halves are load-bearing.
     */
    @Test fun noSourceFileNamesAClassicKeyChooser() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic key-chooser class deleted in S3",
        )
    }

    /** No manifest may declare, or point at, a class this slice deletes. */
    @Test fun noManifestEntryNamesAClassicKeyChooser() {
        assertTrue(
            "src/main/AndroidManifest.xml is missing — this guard would pass vacuously",
            File("src/main/AndroidManifest.xml").isFile,
        )
        val offenders = ClassicRemovalScan.manifestPaths
            .filter { File(it).isFile }
            .flatMap { path ->
                File(path).readLines()
                    .filter { line -> doomedClassRefs.any { it.containsMatchIn(line) } }
                    .map { "$path: ${it.trim()}" }
            }
            .sorted()
        assertEquals(
            "a manifest still names a class S3 deletes — either a leftover <activity> block or " +
                "a parentActivityName. Neither is a compile error and neither breaks another " +
                "test; the app would simply reference a class that is gone.",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The routing arms must be unconditional now: no branch may survive inside one.
     *
     * Batch Z-late epilogue, Task 7: folded onto the shared helper. This copy was already the
     * CORRECT one -- S3 wrote the whole-word `else` detector here, and that is the form
     * [ClassicRemovalScan] was extracted from -- so folding it changes no behaviour; it removes the
     * third and last copy, which is what stops the two broken copies from being re-derived from a
     * surviving inlined one.
     */
    @Test fun screenLauncherDoesNotBranchForKeyChoosers() = ClassicRemovalScan.assertLauncherArmsUnconditional(
        listOf("Screen.ChooseDictionaryWord"),
        "these key-chooser arms still branch on the flag (or are missing entirely) — S3 " +
            "collapses them to the Compose class unconditionally",
    )

    /**
     * #4172: the full-screen general-book and map key choosers (the nav destinations that replaced these classic screens)
     * are gone as well: those keys are chosen in the reading view's key-list quick sheet, from every entry point. Their
     * routes, destinations, deps, controllers, screens and `Screen` entries must not come back in any shipping source,
     * `:app` or shared. The needles are assembled from pieces so this file cannot become its own offender.
     */
    @Test fun theFullScreenGeneralBookAndMapKeyChoosersAreGone() {
        val needles = listOf(
            "CHOOSE_GENERAL" + "_BOOK_KEY", "CHOOSE_MAP" + "_KEY",
            "navigation/chooseGeneral" + "BookKey", "navigation/chooseMap" + "Key\"",
            "ChooseGeneralBookKey" + "Screen", "ChooseMapKey" + "Screen",
            "ChooseGeneralBookKey" + "Controller", "ChooseMapKey" + "Controller",
            "ChooseGeneralBookKey" + "Deps", "ChooseMapKey" + "Deps",
            "Screen.ChooseGeneral" + "BookKey", "Screen.ChooseMap" + "Key",
        )
        val shared = listOf("../sharedCore/src", "../sharedLogic/src", "../sharedUi/src").flatMap { root ->
            java.io.File(root).listFiles { f -> f.isDirectory && !f.name.endsWith("Test") }.orEmpty().flatMap { set ->
                set.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
        }
        check(shared.isNotEmpty()) { "no shared sources found; the scan would go blind" }
        // The retired-route list names the old routes on purpose: a restored start route must open reading.
        val retiredRoutes = java.io.File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/app/AppRoutes.kt").canonicalFile
        val offenders = (ClassicRemovalScan.appSources() + shared)
            .filter { it.canonicalFile != retiredRoutes }
            .filter { file -> file.readText().let { text -> needles.any { it in text } } }
        org.junit.Assert.assertTrue(
            "the full-screen general-book or map key chooser is back at: ${offenders.map { it.path }}", offenders.isEmpty())
    }
}
