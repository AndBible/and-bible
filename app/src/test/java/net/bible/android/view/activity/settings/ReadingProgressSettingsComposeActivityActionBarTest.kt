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
package net.bible.android.view.activity.settings

import net.bible.android.TestBibleApplication
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device-independent guard against the double-app-bar defect documented in
 * `net.bible.android.view.compose.ComposeHostActionBarTest` (2026-07-13): a Compose host Activity
 * that inherits the app default theme (`AppTheme`, `Theme.AppCompat.DayNight.DarkActionBar`) gets a
 * *native* AppCompat ActionBar on top of its own Compose top bar. [ReadingProgressSettingsComposeActivity]
 * declares `android:theme="@style/Theme.AbCompose"` in the manifest (`windowActionBar=false`), so
 * `supportActionBar` must be null — i.e. exactly ONE app bar (the Compose one) is shown, not two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingProgressSettingsComposeActivityActionBarTest {

    @Test
    fun `ReadingProgressSettingsComposeActivity has no native ActionBar (Theme_AbCompose, single app bar)`() {
        val activity = Robolectric.buildActivity(ReadingProgressSettingsComposeActivity::class.java).create().get()
        assertNull(
            "ReadingProgressSettingsComposeActivity must use Theme.AbCompose (NoActionBar); a non-null " +
                "supportActionBar means the manifest entry lost the theme, producing a double app bar.",
            activity.supportActionBar,
        )
    }
}
