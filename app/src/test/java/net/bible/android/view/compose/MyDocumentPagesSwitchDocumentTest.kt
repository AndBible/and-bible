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

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.mydocuments.MyDocumentPagesScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * F60: the page list needs a distinct "change document" top-bar action, separate from the plain
 * back arrow ([MyDocumentPagesScreen]'s `onNavigateUp` stays "previous view", per spec §0). Guards
 * that the action exists and fires [MyDocumentPagesScreen.onSwitchDocument] -- not [onNavigateUp] --
 * when tapped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class MyDocumentPagesSwitchDocumentTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val switchDocumentText: String get() = context.getString(R.string.my_documents_switch_document)

    private val pages = listOf(
        MyDocPageItem(0, "Introduction", ContentType.MARKDOWN, isAiGenerated = false),
    )

    private fun setScreen(onSwitchDocument: () -> Unit = {}, onNavigateUp: () -> Unit = {}) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    MyDocumentPagesScreen(
                        title = "Sermon notes", pages = pages, dirty = false,
                        query = "", filtering = false, searchModeActive = false,
                        totalCount = pages.size,
                        onOpenSearch = {}, onCloseSearch = {}, onQueryChange = {},
                        onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> }, onDelete = {}, onExport = {},
                        onCreate = { _, _ -> }, onImport = {}, onSave = {}, onCancel = {},
                        onNavigateUp = onNavigateUp,
                        onSwitchDocument = onSwitchDocument,
                        selection = emptySet(), onToggleSelected = {}, onClearSelection = {},
                        onDeleteSelected = {}, onExportSelected = {},
                    )
                }
            }
        }
    }

    @Test fun the_page_list_top_bar_offers_a_switch_document_action() {
        setScreen()
        compose.onNodeWithContentDescription(switchDocumentText).assertIsDisplayed()
    }

    @Test fun tapping_switch_document_fires_onSwitchDocument_not_onNavigateUp() {
        var switched = false
        var wentUp = false
        setScreen(onSwitchDocument = { switched = true }, onNavigateUp = { wentUp = true })

        compose.onNodeWithContentDescription(switchDocumentText).performClick()

        assertEquals(true, switched)
        // The switch-document action must NOT be aliased to the up arrow's callback (spec §0: the
        // arrow stays a pure back arrow).
        assertEquals(false, wentUp)
    }
}
