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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks Batch Z-late's epilogue (spec 10.2-10.4): the reading view's classic branches, the
 * classic bottom chrome and everything the collapse orphans are gone, and stay gone.
 *
 * Deliberately source-walking rather than behavioural, following the house pattern
 * (SettingsEditorSheetGuardTest, QuickSheetMountGuardTest, SpeakEntryPointGuardTest): the
 * decision being made permanent here is structural, and no runtime assertion can observe
 * "this branch no longer exists".
 */
class ClassicReadingViewRemovalGuardTest {
    private val mainBibleActivity =
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"

    @Test
    fun theReadingViewReadsTheFlagInExactlyOnePlaceLeft() {
        // Task 1 collapses seven of the nine reads; :2560 (showSystemUI) and :2896
        // (updateToolbar) are Task 2's, and this expectation drops to 0 there.
        val code = ClassicRemovalScan.codeLinesOf(mainBibleActivity)
        assertEquals(
            "MainBibleActivity should read use_compose_ui exactly twice after Task 1 -- " +
                "the two local `val composeUiEnabled` sites Task 2 owns. Any other count means " +
                "a branch was missed or one of Task 2's was collapsed early.",
            2,
            Regex("""getBoolean\("use_compose_ui"""").findAll(code).count(),
        )
    }
}
