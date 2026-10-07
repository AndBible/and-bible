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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch Z-late phase 1, slice S13: the two classic Speak screens were deleted with their two
 * layouts and their shared menu, `SpeakTransportWidget`'s unguarded config-button route was removed
 * (spec §4 P3), and `Screen.BibleSpeak`'s arm was pointed at `MainBibleActivity`.
 *
 * Batch Z-late epilogue, Task 1 (spec decision D3): that arm is now GONE, and so is the enum entry
 * — the epilogue removed the last three `Screen.BibleSpeak` launch sites, leaving a routing target
 * that was fiction. `theBibleSpeakScreenEntryIsGone` below replaces the old
 * `screenLauncherDoesNotBranchForBibleSpeak`, which asserted that the arm existed unconditionally
 * and would now fail for the RIGHT reason with entirely the wrong message. Speak entry is wholly a
 * sheet over the reading view; `SpeakEntryPointGuardTest` guards that sheet.
 *
 * Batch Z-late epilogue, Task 5 (spec 10.4 / decision D1): `SpeakTransportWidget` and its layout
 * moved from this file's PRESENT list to its doomed list. S13 kept them because they were still
 * embedded in `main_bible_view.xml`; Task 5 removed that embedding, and a `GONE` view is still
 * attached, so hiding rather than deleting would have left three live bus subscriptions and a
 * per-tick `getStatusText` running beside the Compose controller.
 *
 * `AbstractSpeakActivity` was deliberately kept referenceless by §2.4 as residue after S13 deleted
 * both its subclasses — the phase's third such residue after ChooseKeyBase (S3) and
 * ProgressActivityBase (S19). nav-graph slice 8 F7 deletes the whole `CustomTitlebarActivityBase`
 * family of zero-subclass residue at once, this base included.
 */
class ClassicSpeakRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.speak.BibleSpeakActivity",
        "net.bible.android.view.activity.speak.SpeakSettingsActivity",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/speak/BibleSpeakActivity.kt",
        "src/main/java/net/bible/android/view/activity/speak/SpeakSettingsActivity.kt",
        "src/main/res/layout/speak_bible.xml",
        "src/main/res/layout/speak_settings.xml",
        "src/main/res/menu/speak_bible_actionbar_menu.xml",
        // Batch Z-late epilogue, Task 5 (spec 10.4 / decision D1): the classic transport bar was
        // the last of the classic bottom chrome. It could not simply be left GONE -- a GONE view is
        // still ATTACHED, so it kept three ABEventBus subscriptions and ran getStatusText on every
        // SpeakProgressEvent beside the Compose SpeakTransportController that replaced it.
        "src/main/java/net/bible/android/view/util/widget/SpeakTransportWidget.kt",
        "src/main/res/layout/speak_transport_widget.xml",
    )

    @Test fun theClassicSpeakFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic Speak files, layouts or the shared actionbar menu should have been " +
                "deleted in S13, and SpeakTransportWidget with its layout in the epilogue's Task 5",
        )
    }

    @Test fun noSourceFileNamesAClassicSpeakScreen() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic Speak screen deleted in S13. Everything under " +
                "speak/actionbarbuttons/ SURVIVES and names neither class — a hit there would be a " +
                "new reference, not a leftover.",
        )
    }

    @Test fun noManifestEntryNamesAClassicSpeakScreen() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S13 deletes. Both blocks go together: main:510-515's " +
                "parentActivityName pointed at main:505-509's class, so neither could outlive the " +
                "other.",
        )
    }

    @Test fun theBibleSpeakScreenEntryIsGone() {
        val code = ClassicRemovalScan.codeLinesOf(ClassicRemovalScan.LAUNCHER_PATH)
        // Anti-vacuity: the scan must be reading a real, populated router, or "no BibleSpeak here"
        // is what an empty string says too.
        assertTrue(
            "${ClassicRemovalScan.LAUNCHER_PATH} does not look like the router any more — this " +
                "assertion would pass vacuously",
            code.contains("Screen.WorkspaceSelector ->"),
        )
        assertFalse(
            "ScreenLauncher still names BibleSpeak. Batch Z-late's epilogue (decision D3) removed " +
                "the enum entry AND its targetFor arm: all three launch sites are gone, so the " +
                "routing target would be fiction. Speak entry is the reading-view sheet only.",
            code.contains("BibleSpeak"),
        )
    }

    @Test fun theReferencelessAbstractSpeakActivityIsGone() {
        ClassicRemovalScan.assertPathsGone(
            listOf("src/main/java/net/bible/android/view/activity/speak/AbstractSpeakActivity.kt"),
            "slice 8 F7: referenceless since S13",
        )
    }

    /**
     * The epilogue did exactly what this guard's older message predicted. S13 called
     * `SpeakTransportWidget` §2.4-protected and asserted it PRESENT, because it removed only the
     * widget's config-button route and left the widget itself embedded in `main_bible_view.xml`.
     * Task 5 removed that embedding, and with it the widget and its layout -- both are now in
     * [doomedPaths] above, which is why this assertion is the inverse of the one it replaces.
     *
     * The promoted hide event's path now survives as a direct write in the transport service,
     * pinned below so removing the widget cannot silently break the Compose Speak bar's hide path.
     *
     * The hide path that `HideTransportEvent` carried after the classic widget went is now a direct
     * write in [SpeakTransportServiceImpl.stop] (ABEventBus removal phase 6). Pins it: without it,
     * Stop on a stopped transport would no longer hide the Compose Speak bar.
     */
    @Test fun theTransportBarsHidePathOutlivedTheWidget() {
        val code = ClassicRemovalScan.codeLinesOf(
            "src/main/java/net/bible/android/control/speak/SpeakTransportServiceImpl.kt",
        )
        assertTrue(
            "SpeakTransportServiceImpl.stop() no longer hides the bar when speech is already stopped",
            code.contains("_state.value = _state.value.copy(visible = false)"),
        )
    }
}
