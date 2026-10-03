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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.ShareVersesEntry
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.reading.ShareVersesSheetContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 26 (platform-dialog removal, run 3): [ShareVersesSheetContent] is [ShareVersesSheet][
 * net.bible.sharedui.reading.ShareVersesSheet]'s body, tested directly here rather than through the
 * shell — an open `ModalBottomSheet` hangs a Robolectric idle forever and must never be captured or
 * driven in a test, per this run's "Hang protection" rule and [ShareVersesSheet]'s own kdoc.
 *
 * Covers exactly what `run3-context.md`'s Robolectric guidance calls for: the preview text tracks
 * the live [ShareVersesOptions] (a switch toggle rebuilds it, with no JSword lookup involved — see
 * [ShareVersesOptions.buildText]'s kdoc), and Share/Copy call their callbacks with that SAME preview
 * text, not a stale or independently recomputed one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ShareVersesSheetContentTest {
    @get:Rule val compose = createComposeRule()

    private val input = ShareVersesInput(
        verses = listOf(ShareVersesEntry(1, "In the beginning God created the heaven and the earth.")),
        startOffset = 0,
        endOffset = null,
        referenceAbbreviated = "Gen 1:1",
        referenceFull = "Genesis 1:1",
        versionAbbreviation = "KJV",
        notesText = null,
        advertiseText = "Shared from AndBible Bible Study (https://andbible.github.io)",
        hasRange = false,
    )

    private var lastShared: String? = null
    private var lastCopied: String? = null

    private fun show(initialOptions: ShareVersesOptions = ShareVersesOptions(advertiseApp = false)) {
        compose.setContent {
            var options by remember { mutableStateOf(initialOptions) }
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    ShareVersesSheetContent(
                        options = options,
                        onOptionsChange = { options = it },
                        input = input,
                        onShare = { lastShared = it },
                        onCopy = { lastCopied = it },
                    )
                }
            }
        }
    }

    @Test fun previewShowsTheBuiltTextForTheStartingOptions() {
        show()
        // Default options: showReferenceAtFront=true, abbreviateReference=true, showVersion=true.
        compose.onNodeWithText("Gen 1:1 KJV In the beginning God created the heaven and the earth.").assertExists()
    }

    @Test fun togglingAbbreviateReferenceUpdatesThePreviewWithNoJswordCall() {
        show()
        compose.onNodeWithText("Abbreviate reference").performClick()
        compose.onNodeWithText("Genesis 1:1 KJV In the beginning God created the heaven and the earth.").assertExists()
    }

    @Test fun togglingShowVersionUpdatesThePreview() {
        show()
        compose.onNodeWithText("Include version name").performClick()
        compose.onNodeWithText("Gen 1:1 In the beginning God created the heaven and the earth.").assertExists()
    }

    @Test fun shareCallsOnShareWithTheCurrentPreviewText() {
        show()
        compose.onNodeWithText("Share").performClick()
        assertEquals("Gen 1:1 KJV In the beginning God created the heaven and the earth.", lastShared)
        assertEquals(null, lastCopied)
    }

    @Test fun copyCallsOnCopyWithTheCurrentPreviewText() {
        show()
        compose.onNodeWithText("Copy").performClick()
        assertEquals("Gen 1:1 KJV In the beginning God created the heaven and the earth.", lastCopied)
        assertEquals(null, lastShared)
    }

    @Test fun shareReflectsAToggleMadeBeforeTapping() {
        show()
        compose.onNodeWithText("Abbreviate reference").performClick()
        compose.onNodeWithText("Share").performClick()
        assertEquals("Genesis 1:1 KJV In the beginning God created the heaven and the earth.", lastShared)
    }

    @Test fun selectionOnlyAndNotesRowsAreHiddenWhenNotApplicable() {
        // input.hasRange = false and input.notesText = null -- both conditionally-emitted rows
        // must be absent (ShareVersesSheetContent's GONE-when-not-applicable shape).
        show()
        compose.onNodeWithText("Show selected text only").assertDoesNotExist()
        compose.onNodeWithText("Show ellipsis (…)").assertDoesNotExist()
        compose.onNodeWithText("Show notes").assertDoesNotExist()
    }
}
