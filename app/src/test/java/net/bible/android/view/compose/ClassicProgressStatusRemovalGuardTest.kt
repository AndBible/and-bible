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
 * Slice S19 deleted the classic progress-status screen: one activity, one arm, one manifest
 * block, and the instrumented test that drove it. This guard makes that permanent — see
 * [ClassicRemovalScan] for why a deletion needs a test at all (dead code compiles cleanly, so the
 * compiler proves nothing about it).
 */
class ClassicProgressStatusRemovalGuardTest {

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/download/ProgressStatus.kt",
    )

    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.download.ProgressStatus",
    )

    /**
     * The resources this slice deliberately KEEPS. `progress_status.xml`'s only `R.layout`
     * referrer is the deleted class, so spec 8.1's last-referrer rule would call it doomed too —
     * but three of its ids (`progressControlContainer`, `noTasksRunning`, `progressStatusMessage`)
     * are read by the surviving, spec-2.4-protected `ProgressActivityBase`, and this layout is
     * their sole definition anywhere under any source set's `res` tree. Deleting it would be an
     * unresolved-reference compile error in a file this batch forbids touching. (The layout's
     * other two ids die with the class that alone reads them: `okButton` is read only by the
     * deleted `ProgressStatus.kt`, and `button_panel` is read nowhere at all — it merely happens
     * to share a name with an unrelated id in `speak_transport_widget.xml`.)
     */
    private val survivingCollaborators = listOf(
        "src/main/res/layout/progress_status.xml",
        "src/main/java/net/bible/android/view/activity/base/ProgressActivityBase.kt",
    )

    @Test fun theClassicProgressStatusFilesAreGone() =
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "a classic progress-status source is back; slice S19 deleted it, so " +
                "ProgressStatusComposeActivity is the only implementation",
        )

    @Test fun theSurvivingProgressStatusCollaboratorsStillExist() =
        ClassicRemovalScan.assertPathsPresent(
            survivingCollaborators,
            "a collaborator slice S19 deliberately kept has been deleted: progress_status.xml is " +
                "the sole definer of three ids ProgressActivityBase still reads, and " +
                "ProgressActivityBase itself is spec 2.4 residue (zero subclasses after this slice)",
        )

    @Test fun noSourceFileNamesAClassicProgressStatusClass() =
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "a shipping source file or resource XML still names the classic ProgressStatus class; " +
                "the fix is to remove the reference (for a layout, the offending TAG), not to " +
                "restore the class",
        )

    @Test fun noManifestEntryNamesAClassicProgressStatusClass() =
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names the classic ProgressStatus class -- check android:name AND " +
                "android:parentActivityName; the latter compiles, tests and renders fine while " +
                "pointing Up at a class that does not exist",
        )

    @Test fun theProgressStatusArmIsUnconditional() =
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.ProgressStatus"),
            "the ProgressStatus arm still branches; slice S19 deleted the classic " +
                "class it would branch to",
        )
}
