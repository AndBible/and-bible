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
 * Slice S18 collapsed the dead `Screen.Startup` arm. Unlike every other guard in this phase it
 * deletes NO file, so `assertPathsGone`/`assertNoSourceNames` would be actively wrong here:
 * classic `StartupActivity` survives as the manifest launcher's target and the pre-init
 * orchestrator, and `startup_view.xml` plus its nine private collaborators survive too, because
 * `showFirstLayout()` has a second caller inside `gotoMainBibleActivity()` that is reachable with
 * `use_compose_ui` ON whenever every installed Bible is locked (spec Appendix B A4 says otherwise
 * and is wrong; see the batch record). Those move to the epilogue with the pre-init splash.
 *
 * So this guard asserts exactly two things: the arm no longer branches, and the class it used to
 * branch to is still here — the second being what stops a later slice from reading the first as
 * permission to delete it.
 */
class ClassicStartupArmRemovalGuardTest {

    @Test fun theStartupArmIsUnconditional() =
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.Startup"),
            "the Screen.Startup arm consults use_compose_ui again; its classic half was never " +
                "reachable -- the only caller of targetFor(Screen.Startup) sits inside " +
                "if (useComposeFor(Screen.Startup)), and with the flag OFF the arm returned a " +
                "class with no <activity> entry in any manifest, so it would have thrown " +
                "ActivityNotFoundException",
        )

    @Test fun classicStartupActivityAndItsFirstRunLayoutStillExist() =
        ClassicRemovalScan.assertPathsPresent(
            listOf(
                "src/main/java/net/bible/android/view/activity/StartupActivity.kt",
                "src/main/java/net/bible/android/activity/StartupActivity.kt",
                "src/main/res/layout/startup_view.xml",
                "src/main/res/layout/spinner.xml",
            ),
            "S18 collapsed a routing arm, it did NOT delete the classic startup screen: " +
                "StartupActivity is still the manifest launcher's target and the pre-init " +
                "orchestrator, the legacy alias subclass is named by CommonUtils.changeAppIconAndName, " +
                "and startup_view.xml is still reachable with use_compose_ui ON via " +
                "gotoMainBibleActivity()'s all-Bibles-locked fallback",
        )
}
