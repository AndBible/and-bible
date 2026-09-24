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
import org.junit.Test

/**
 * Slice S18 collapsed the dead `Screen.Startup` arm. Slice 8 E4 deleted `startup_view.xml` and
 * `showFirstLayout()` — M1 — so the second assertion of this guard changed from "still exists" to
 * "is gone": classic `StartupActivity` survives as the manifest launcher's target and the boot
 * orchestrator, but the first-run welcome it used to show inline is gone, replaced by the
 * every-Bible-locked path opening the nav host's Compose `NavRoutes.WELCOME` destination instead.
 *
 * So this guard asserts exactly two things: the arm no longer branches, and the classic layout it
 * used to show is gone for good — the second being what stops a later slice from reading the first
 * as permission to bring it back.
 */
class ClassicStartupArmRemovalGuardTest {

    @Test fun theStartupArmIsUnconditional() =
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.Startup"),
            "the Screen.Startup arm branches again; its classic half was never reachable -- " +
                "the arm's classic side named a class with no <activity> entry in any manifest, " +
                "so taking it would have thrown ActivityNotFoundException",
        )

    /**
     * Slice 8 E4 (finding M1): the classic XML welcome is GONE. It was the last live classic-XML screen,
     * reached from `gotoMainBibleActivity()`'s every-Bible-locked path; that path now opens the nav host's
     * Compose WELCOME, which honours discrete mode (F62). Guards the defect coming back: the layout, its
     * binding or `showFirstLayout` reappearing.
     */
    @Test fun theClassicFirstRunLayoutIsGone() {
        ClassicRemovalScan.assertPathsGone(
            listOf("src/main/res/layout/startup_view.xml"),
            "the classic XML welcome must not come back -- the locked-Bible path opens NavRoutes.WELCOME",
        )
        val startup = ClassicRemovalScan.codeLinesOf("src/main/java/net/bible/android/view/activity/StartupActivity.kt")
        assertFalse("StartupActivity must not show a layout of its own beyond the splash", startup.contains("showFirstLayout"))
        assertFalse("…nor bind the deleted layout", startup.contains("StartupViewBinding"))
    }

    @Test fun startupActivityAndItsSplashStillExist() =
        ClassicRemovalScan.assertPathsPresent(
            listOf(
                "src/main/java/net/bible/android/view/activity/StartupActivity.kt",
                "src/main/java/net/bible/android/activity/StartupActivity.kt",
                "src/main/res/layout/spinner.xml",
            ),
            "StartupActivity stays as the launcher's target and the boot orchestrator (slice 8 approach A); " +
                "the legacy alias subclass is named by CommonUtils.changeAppIconAndName; spinner.xml is its splash",
        )
}
