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
 * Batch Z-late phase 1, slice S15: the classic calculator — the discrete build's disguise and the
 * whole port's pilot screen — was deleted with its layout, and `ScreenLauncher`'s arm collapsed to
 * `CalculatorComposeActivity`.
 *
 * Four assertions, not five: nothing becomes referenceless. `CalculatorActivity` extended
 * `ActivityBase` directly and imported nothing from the two §2.4-protected directories.
 *
 * Two things this guard deliberately does NOT assert, both of which look like omissions:
 *
 * 1. `ScreenLauncherTest` has no `calculator_routes_to_compose` case to rewrite, and never had one.
 *    `Screen.Calculator` appears there only as the probe for `useComposeFor` itself
 *    (`default_off_routes_to_old`, `flag_on_routes_to_new`), which must survive until the flag dies
 *    in the epilogue. `screenLauncherDoesNotBranchForCalculator` below is therefore the only check
 *    that this arm is unconditional — spec Appendix A11's "nothing fails if an arm silently loses
 *    its test" made concrete, on the pilot screen of all things.
 * 2. The `<activity-alias>` named `net.bible.android.view.activity.Calculator` is untouched: its
 *    `targetActivity` is `.StartupActivity`, not this class.
 */
class ClassicCalculatorRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.discrete.CalculatorActivity",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/discrete/CalculatorActivity.kt",
        "src/main/res/layout/calculator_layout.xml",
        "src/test/java/net/bible/android/view/activity/discrete/CalculatorActivityTest.kt",
    )

    @Test fun theClassicCalculatorFilesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "the classic calculator, its layout or its Robolectric suite should have been deleted " +
                "in S15. The layout has no R.layout referrer anywhere — it was reached only through " +
                "the generated CalculatorLayoutBinding — so ownership was established from the " +
                "binding name, not the resource name.",
        )
    }

    @Test fun noSourceFileNamesTheClassicCalculator() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name the classic CalculatorActivity deleted in S15. The surviving " +
                "twin is CalculatorComposeActivity, whose name does not contain the doomed FQN as a " +
                "contiguous substring, so a hit here is real.",
        )
    }

    @Test fun noManifestEntryNamesTheClassicCalculator() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names the class S15 deletes. Note that the <activity-alias> spelled " +
                "net.bible.android.view.activity.Calculator is NOT this class — its targetActivity " +
                "is .StartupActivity — and must not be removed to make this pass.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForCalculator() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.Calculator"),
            "the Calculator arm still branches on the flag (or is missing entirely) — S15 collapses " +
                "it to CalculatorComposeActivity unconditionally",
        )
    }
}
