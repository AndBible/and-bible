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
 * Batch Z-late phase 1, slice S12 -- the LAST classic Screen: six classic settings screens deleted
 * with `PreferenceSearchHelper`, eight resources and six manifest blocks, and `ScreenLauncher`'s
 * four remaining flag arms collapsed to their Compose implementations. After this slice
 * `ScreenLauncher.targetFor` is a plain `Screen -> Class` map with no `useComposeFor` call left
 * anywhere in it (the declaration itself survives for the epilogue to remove).
 *
 * Unlike every earlier slice's guard, this one was written AFTER the deletion (Tasks 3-5), not
 * before it, so it never ran red against a live classic file. Tasks 3-5 are a pure-compile
 * sequence -- their failure mode is the compiler refusing to build a dangling reference, not a
 * test -- so there was no red phase for this guard to sit in front of. The mutation proof that
 * substitutes for it (restore one deleted path, watch [theClassicSettingsFilesAreGone] fail, put it
 * back) lives in this task's report, not in the tree.
 *
 * Five assertions, not four: [theIconParityFixturesAreStillHere] defends the one deliberate
 * exception this slice keeps -- see its own KDoc.
 */
class ClassicSettingsRemovalGuardTest {

    @Test fun theClassicSettingsFilesAreGone() = ClassicRemovalScan.assertPathsGone(
        listOf(
            "src/main/java/net/bible/android/view/activity/settings/SettingsActivity.kt",
            "src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt",
            "src/main/java/net/bible/android/view/activity/settings/TextDisplaySettings.kt",
            "src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt",
            "src/main/java/net/bible/android/view/activity/settings/BackgroundImageChooserActivity.kt",
            "src/main/java/net/bible/android/view/activity/progress/ReadingProgressSettings.kt",
            "src/main/java/net/bible/service/common/PreferenceSearchHelper.kt",
            "src/main/res/xml/text_display_settings.xml",
            "src/main/res/xml/color_settings.xml",
            "src/main/res/layout/settings_activity.xml",
            "src/main/res/layout/settings_dialog.xml",
            "src/main/res/layout/background_image_chooser.xml",
            "src/main/res/layout/background_image_chooser_item.xml",
            "src/main/res/menu/text_options_opts.xml",
            "src/main/res/menu/app_prefs_options.xml",
        ),
        "these classic Settings files or resources should have been deleted in S12. " +
            "settings_dialog.xml and app_prefs_options.xml are inflated/consumed with no " +
            "snake_case-name trail (SettingsDialogBinding, an options-menu XML), so a grep for " +
            "their own name finds no consumer and reads as 'already dead' -- same trap S10 hit " +
            "with its own ViewBinding-inflated layouts. See this class's KDoc for what was KEPT.",
    )

    /**
     * The three PreferenceScreens [net.bible.android.view.activity.settings.SettingsIconParityTest]
     * reads through R.xml, kept on purpose when their activities were deleted -- see that test and
     * this slice's plan, decision D6. A tail sweep looking for orphaned resources under
     * res/xml/settings.xml, res/xml/sync_settings.xml or res/xml/reading_progress_settings.xml will
     * find them referenced by nothing but a test; that is the intent, not an oversight -- deleting
     * them would keep the compile green (`SettingsIconParityTest` would simply stop compiling, which
     * IS how it would be caught) but lose the drift-from-classic property that is the test's entire
     * point, and a hand-copied table would keep a check while losing that same property. Same call
     * S10 made for prompt_advanced_settings.xml.
     */
    @Test fun theIconParityFixturesAreStillHere() = ClassicRemovalScan.assertPathsPresent(
        listOf(
            "src/main/res/xml/settings.xml",
            "src/main/res/xml/sync_settings.xml",
            "src/main/res/xml/reading_progress_settings.xml",
        ),
        "S12 deleted a file it was supposed to keep. All three are read by " +
            "SettingsIconParityTest through R.xml and compared against sharedUi's icon table -- " +
            "deleting them breaks that test's compile, and losing them silently loses the only " +
            "defence against the Compose settings screens drifting from classic's icon choices.",
    )

    /**
     * These nine names are BARE, not fully-qualified -- unlike every other slice's version of this
     * assertion. [ClassicRemovalScan.assertNoSourceNames] matches by `contains` with only a
     * TRAILING boundary (see its own KDoc), so `"SettingsActivity"` also matches the doomed
     * `SyncSettingsActivity`/`TextDisplaySettingsActivity`/`ReadingProgressSettingsActivity` --
     * harmless, since all four are deleted together in this slice. What matters is the opposite
     * direction: none of these nine names may appear INSIDE a name that survives. It does not:
     * `SettingsComposeActivity` reads `Settings` + `ComposeActivity`, not `Settings` + `Activity`,
     * so `"SettingsActivity"` does not match it, and the same holds for the other three Compose
     * twins (`SyncSettingsComposeActivity`, `TextDisplaySettingsComposeActivity`,
     * `ReadingProgressSettingsComposeActivity`). Confirmed by running this test, not by eye.
     */
    @Test fun noSourceNamesTheClassicSettingsScreens() = ClassicRemovalScan.assertNoSourceNames(
        listOf(
            "SettingsActivity", "SyncSettingsActivity", "TextDisplaySettingsActivity",
            "ReadingProgressSettingsActivity", "ColorSettingsActivity", "BackgroundImageChooserActivity",
            "PreferenceStore", "DirtyTypesSerializer", "setupPreferenceSearch",
        ),
        "these files still name a classic Settings screen or collaborator deleted in S12. Note " +
            "these names are BARE (not fully-qualified), which is deliberate here: the four " +
            "screen names also match their own Compose twins' SUBSTRING relationship in reverse " +
            "-- SettingsComposeActivity does NOT contain SettingsActivity as a substring (it reads " +
            "Settings + ComposeActivity), so the bare form is safe. See this test's own KDoc.",
    )

    @Test fun noManifestNamesTheClassicSettingsScreens() = ClassicRemovalScan.assertNoManifestNames(
        listOf(
            "net.bible.android.view.activity.settings.SettingsActivity",
            "net.bible.android.view.activity.settings.SyncSettingsActivity",
            "net.bible.android.view.activity.settings.TextDisplaySettingsActivity",
            "net.bible.android.view.activity.settings.ColorSettingsActivity",
            "net.bible.android.view.activity.settings.BackgroundImageChooserActivity",
            "net.bible.android.view.activity.progress.ReadingProgressSettingsActivity",
        ),
        "a manifest still names a class S12 deletes. S12 has no repoint leg at all -- unlike " +
            "every earlier slice, both parentActivityName attributes naming a doomed class here " +
            "sat on manifest blocks deleted whole, so there was nothing to repoint onto a " +
            "surviving Compose class before this deletion landed.",
    )

    @Test fun theSettingsArmsResolveUnconditionally() = ClassicRemovalScan.assertLauncherArmsUnconditional(
        listOf("Settings", "SyncSettings", "TextDisplaySettings", "ReadingProgressSettings").map { "Screen.$it" },
        "one of these four settings arms still branches on the flag (or is missing entirely) -- " +
            "S12 collapses all four to their Compose classes unconditionally. These are the LAST " +
            "four arms in ScreenLauncher.targetFor; after this slice the when-block is a plain " +
            "Screen -> Class map with no useComposeFor call left inside it.",
    )
}
