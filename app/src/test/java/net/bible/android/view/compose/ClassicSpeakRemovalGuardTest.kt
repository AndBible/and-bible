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

import org.junit.Test

/**
 * Batch Z-late phase 1, slice S13: the two classic Speak screens were deleted with their two
 * layouts and their shared menu, `SpeakTransportWidget`'s unguarded config-button route was removed
 * (spec §4 P3), and `Screen.BibleSpeak`'s arm was pointed at `MainBibleActivity`.
 *
 * `Screen.BibleSpeak` is the one enum entry with no Activity of its own: round 13a replaced the
 * Compose Speak activities with a sheet over the reading view, so the arm resolves to the host of
 * that sheet. `screenLauncherDoesNotBranchForBibleSpeak` below is therefore a vacuous-but-correct
 * assertion — the arm never had a flag branch to collapse — and it is kept so that a later change
 * cannot quietly reintroduce one.
 *
 * `AbstractSpeakActivity` is deliberately kept and deliberately referenceless: §2.4 names this base
 * and this exact situation. It is the phase's third such residue after ChooseKeyBase (S3) and
 * ProgressActivityBase (S19), and nothing but the assertion below stops a later tidy deleting it
 * with every gate green.
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
    )

    @Test fun theClassicSpeakFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic Speak files, layouts or the shared actionbar menu should have been " +
                "deleted in S13",
        )
    }

    @Test fun noSourceFileNamesAClassicSpeakScreen() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic Speak screen deleted in S13. SpeakTransportWidget and " +
                "everything under speak/actionbarbuttons/ SURVIVE and name neither class — a hit " +
                "there would be a new reference, not a leftover.",
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

    @Test fun screenLauncherDoesNotBranchForBibleSpeak() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.BibleSpeak"),
            "the BibleSpeak arm branches on the flag, or is missing entirely. It must be the single " +
                "unconditional line `Screen.BibleSpeak -> MainBibleActivity::class.java`: there is " +
                "no Speak Activity of either kind any more, and the arm resolves to the host of the " +
                "sheet that replaced them.",
        )
    }

    @Test fun theReferencelessAbstractSpeakActivityAndTheTransportWidgetStillExist() {
        ClassicRemovalScan.assertPathsPresent(
            listOf(
                "src/main/java/net/bible/android/view/activity/speak/AbstractSpeakActivity.kt",
                "src/main/java/net/bible/android/view/util/widget/SpeakTransportWidget.kt",
                "src/main/res/layout/speak_transport_widget.xml",
            ),
            "AbstractSpeakActivity lost both its subclasses in S13 and is now referenceless — §2.4 " +
                "names it and keeps it. SpeakTransportWidget is §2.4-protected too: S13 removes its " +
                "config-button route but not the widget, which stays until the epilogue removes the " +
                "classic bottom chrome from main_bible_view.xml.",
        )
    }
}
