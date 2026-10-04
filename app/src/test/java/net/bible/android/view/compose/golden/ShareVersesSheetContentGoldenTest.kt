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

package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.reading.ShareVersesEntry
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.sharedui.reading.ShareVersesSheetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M1 (run 3 final review, "must fix before merge"): [ShareVersesSheetContent] (Task 26) had no
 * golden at all -- the Global Constraint "Theme variants" requires `captureMatrix` + `captureRtl`
 * for every new composable, same as every other `…Content` golden in this package
 * ([AbSheetWrappersGoldenTest], [DocumentQuickGoldenTest], [AiConnectionSettingsGoldenTest]'s
 * `RetentionSheetContent`, …).
 *
 * Captured directly, wrapped in [SheetSurface] (background-colour consistency with every other
 * sheet-content capture in this package), never [net.bible.sharedui.reading.ShareVersesSheet]
 * itself -- an open `ModalBottomSheet` hangs Roborazzi and takes the whole `:app` suite with it
 * (`ShareVersesSheet`'s own kdoc says so, and `SettingsEditorSheetGuardTest` polices it by name).
 *
 * [input] sets `hasRange = true` and a non-null `notesText`, unlike
 * [net.bible.android.view.compose.ShareVersesSheetContentTest]'s minimal fixture -- so every
 * conditionally-emitted row ("Show selected text only", "Show ellipsis (…)", "Show notes") is
 * captured too, not just the seven unconditional switches; a golden with the narrower fixture would
 * never render those rows at all. [options] leaves every toggle at a non-default value it can
 * (`showSelectionOnly`/`showEllipsis`/`showNotes`/`advertiseApp` all true) so the RTL capture also
 * exercises M2's known gap (an RTL selection's preview stays LTR, recorded separately, not fixed
 * here) with a realistic, fully-populated sheet rather than an all-defaults one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ShareVersesSheetContentGoldenTest {

    private val input = ShareVersesInput(
        verses = listOf(
            ShareVersesEntry(1, "In the beginning God created the heaven and the earth."),
            ShareVersesEntry(2, "And the earth was without form, and void."),
        ),
        startOffset = 0,
        endOffset = null,
        referenceAbbreviated = "Gen 1:1-2",
        referenceFull = "Genesis 1:1-2",
        versionAbbreviation = "KJV",
        notesText = "A note on the opening verses.",
        advertiseText = "Shared from AndBible Bible Study (https://andbible.github.io)",
        hasRange = true,
    )

    private val options = ShareVersesOptions(
        showReference = true,
        abbreviateReference = true,
        showVersion = true,
        showReferenceAtFront = true,
        showVerseNumbers = true,
        showQuotes = false,
        separateVersesWithNewlines = false,
        showSelectionOnly = true,
        showEllipsis = true,
        showNotes = true,
        advertiseApp = true,
    )

    @Test fun share_matrix() = captureMatrix("ShareVersesSheetContent", "share", heightDp = 920) {
        SheetSurface {
            ShareVersesSheetContent(options = options, onOptionsChange = {}, input = input, onShare = {}, onCopy = {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun share_rtl() = captureRtl("ShareVersesSheetContent", "share", heightDp = 920) {
        SheetSurface {
            ShareVersesSheetContent(options = options, onOptionsChange = {}, input = input, onShare = {}, onCopy = {})
        }
    }
}
