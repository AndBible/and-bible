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

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.page.screen.bibleToolbarIconRes
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F58: discrete mode ("calculator disguise") must reach the toolbar icon and drawer identity that
 * the Compose reading chrome resolves HOST-side (`:app`), exactly as `DocumentBadges.kt`'s
 * `BookCategory.imageResource` (`:63`) and classic `MainBibleActivity.kt:563` already do for their
 * own call sites. `:sharedUi` stays platform-clean: it only ever receives ready-made resource ids.
 *
 * `discrete_mode` is read by [CommonUtils.isDiscrete] from [CommonUtils.realSharedPreferences], NOT
 * [CommonUtils.settings] (a separate, `SettingsDatabase`-backed store) --
 * `AppSettingsServiceImplTest.realSharedKey_discreteMode_writesToRealSharedPreferences_notSettingsDb`
 * pins that routing, so the flag is set directly on `realSharedPreferences` here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
class DiscreteChromeTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun drawerMenuState() = DrawerMenuStateBuilder.build(
        showSearch = true, showSpeak = true, isCloudSyncAvailable = true, isRateVisible = true,
    )

    @After
    fun tearDown() {
        CommonUtils.realSharedPreferences.edit().remove("discrete_mode").apply()
    }

    @Test
    fun discreteModeSwapsTheBibleIconAndTheDrawerIdentity() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).apply()

        assertEquals(
            "discrete mode must swap the toolbar's Bible icon, as classic does " +
                "(MainBibleActivity.kt:563) and as DocumentBadges.kt:63 already does",
            R.drawable.ic_baseline_menu_book_24,
            bibleToolbarIconRes(),
        )
        assertEquals(
            "and the drawer header must not leak the real app name",
            context.getString(R.string.app_name_calculator),
            drawerMenuState().appName,
        )
        assertEquals(
            "nor the real logo",
            R.drawable.ic_calculator_color,
            ComposeReadingViewHost.drawerIconResIdFor("ic_logo"),
        )
    }

    /**
     * Fix round 2 (R13, Task 22's full-unit-suite finding): [ComposeReadingViewHost.drawerIconResIds]
     * is a companion-object `val` whose old (pre-fix) `"ic_logo"` entry inlined the discrete check --
     * its initializer ran ONCE, at first touch, whichever way `CommonUtils.isDiscrete` read at that
     * moment. In the full suite an earlier test touched the companion with discrete OFF, so
     * `"ic_logo"` baked in the real logo forever after -- turning discrete mode on later never
     * changed an already-computed map entry. [ComposeReadingViewHost.drawerIconResIdFor] fixes this
     * by resolving `"ic_logo"` at CALL time instead of table-init time; this test reproduces the
     * ordering that broke the old table (touch it OFF first, forcing companion-object init, THEN
     * flip discrete ON) and asserts the call-time function is immune to it.
     */
    @Test
    fun drawerLogoResolvesToTheCurrentDiscreteStateEvenAfterAnEarlierOffTouch() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", false).apply()
        ComposeReadingViewHost.drawerIconResIds["ic_logo"] // force companion-object init while OFF

        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).apply()

        assertEquals(
            "the drawer logo must reflect discrete mode NOW, not whatever isDiscrete answered the " +
                "first time anything touched the table (fix round 2 / R13) -- an init-once val " +
                "bakes in the value read at first touch and never re-evaluates it",
            R.drawable.ic_calculator_color,
            ComposeReadingViewHost.drawerIconResIdFor("ic_logo"),
        )
    }
}
