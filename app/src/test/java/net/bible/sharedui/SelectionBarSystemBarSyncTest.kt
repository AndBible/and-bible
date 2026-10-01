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
package net.bible.sharedui

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.theme.LocalSystemBarSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `AbSelectionScaffold`'s selection branch rendered a raw `TopAppBar` and never called
 * `SyncSystemBars`, so on the six selection-capable screens the status-bar colour and icon
 * appearance stayed whatever the NORMAL branch last set for as long as selection was active.
 * Host-inset-ownership spec, section 3.4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SelectionBarSystemBarSyncTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectionModeSyncsTheSystemBars() {
        val seen = mutableListOf<Color>()
        var expectedContainer: Color? = null
        compose.setContent {
            ProvideAppLocals {
            CompositionLocalProvider(
                LocalSystemBarSync provides { container, _ -> seen.add(container) }
            ) {
                // Same expression `AbTopAppBar`'s normal branch reads for its own SyncSystemBars
                // call, so the two branches cannot drift apart.
                expectedContainer = TopAppBarDefaults.topAppBarColors().containerColor
                AbSelectionScaffold(
                    title = "Bookmarks",
                    selectionMode = true,
                    selectedCount = 2,
                    onNavigateUp = {},
                    onExitSelection = {},
                ) { }
            }
            }
        }
        compose.waitForIdle()
        assertTrue("selection mode must call SyncSystemBars", seen.isNotEmpty())
        assertEquals(
            "selection mode must sync the same container colour its TopAppBar actually uses",
            expectedContainer,
            seen.last(),
        )
    }
}
