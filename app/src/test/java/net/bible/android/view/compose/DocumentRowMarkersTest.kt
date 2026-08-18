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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.navigation.DocumentRow
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Mandatory wrapping (see DocumentFilterBarTest): DocumentRow reads LocalStrings.current and
// LocalCategoryIcon.current, both staticCompositionLocalOf with no default, so a bare
// setContent crashes at render time.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DocumentRowMarkersTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val recommendedText: String get() = context.getString(R.string.recommended_document)
    private val badWarnText: String get() = context.getString(R.string.bad_document_warning)

    private fun row(recommended: Boolean = false, badWarn: Boolean = false) = DocRow(
        docId = "KJV",
        osisId = "KJV",
        abbreviation = "KJV",
        name = "King James Version",
        language = LangOption("en", "English", "en"),
        repository = "CrossWire",
        category = DocCategory.BIBLE,
        installStatus = DocInstallStatus.NOT_INSTALLED,
        percentDone = 0,
        recommended = recommended,
        badWarn = badWarn,
        locked = false,
        enciphered = false,
        canDelete = false,
        installSizeMb = 4.2,
    )

    private fun setRow(row: DocRow, selectionMode: Boolean = false, widthDp: Int = 360) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    // Robolectric's default test window is 320dp wide with no qualifiers set, well
                    // below a real Android phone's ~360-410dp -- and the property this file's
                    // subtitle-overflow test cares about ("at a realistic phone width the install
                    // size stays readable") is meaningless without pinning a stated, realistic
                    // width. 360dp is the narrow end of that range, so it is also the strictest
                    // realistic case. A second test below deliberately pins a narrower width to
                    // show the field order (not just the width) is what keeps the size readable.
                    Box(Modifier.width(widthDp.dp)) {
                        DocumentRow(
                            row = row,
                            downloadMode = true,
                            selectionMode = selectionMode,
                            selected = selectionMode,
                            onClick = {},
                            onLongClick = {},
                            onDownload = {},
                            onCancel = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun a_recommended_row_shows_the_star_outside_selection_mode() {
        setRow(row(recommended = true))
        compose.onNodeWithContentDescription(recommendedText).assertIsDisplayed()
    }

    @Test fun the_star_is_gone_in_selection_mode_so_it_cannot_land_on_the_checkbox() {
        setRow(row(recommended = true), selectionMode = true)
        compose.onNodeWithContentDescription(recommendedText).assertDoesNotExist()
    }

    @Test fun a_recommended_row_prefixes_the_subtitle_with_the_caption() {
        setRow(row(recommended = true))
        // Field order is [caption ·] size · language · repository: the install size is the
        // number a download decision needs, so it comes right after the caption and the
        // repository -- the least decision-relevant field -- is last (see buildSubtitle KDoc).
        compose.onNodeWithText("$recommendedText · 4.2 MB · English · CrossWire", substring = true)
            .assertIsDisplayed()
    }

    @Test fun a_plain_row_has_neither_the_star_nor_the_caption() {
        setRow(row())
        compose.onNodeWithContentDescription(recommendedText).assertDoesNotExist()
        compose.onNodeWithText(recommendedText, substring = true).assertDoesNotExist()
    }

    /**
     * The subtitle's semantics carry the FULL string even when the line is visually ellipsized, so
     * an `onNodeWithText` assertion cannot see this defect at all — only the laid-out result can.
     * With `maxLines = 1` the bold "Recommended!" caption pushed the repository and the install
     * size off the end of the single line, so the one number a download decision needs was missing
     * precisely on the documents the user is most likely to pick.
     */
    @Test fun a_recommended_download_row_still_shows_the_install_size() {
        setRow(row(recommended = true))
        val layout = subtitleLayout()
        val full = layout.layoutInput.text.text
        // Not endsWith: the field order now puts the size right after the caption (it is the
        // repository that trails and gets truncated instead, per buildSubtitle's KDoc), but this
        // test's job is only "the size is present and the line isn't truncated at this width".
        assertTrue(full.contains("4.2 MB"), "subtitle should contain the install size, was: '$full'")
        assertFalse(
            layout.hasVisualOverflow,
            "the recommended row's subtitle is truncated, so the install size is not readable: '$full'",
        )
    }

    /**
     * At a deliberately narrow 280dp (below the 360dp width the previous test pins -- and,
     * measured, below the width at which this content still fits: at 320dp this exact row does
     * NOT overflow, at 280dp it does), the subtitle DOES overflow -- but the field order
     * (`[caption ·] size · language · repository`) puts the install size right after the caption,
     * so it is still among the visibly PAINTED characters even though the line as a whole is
     * truncated. Checking the semantics text (`onNodeWithText`) cannot show this: it always
     * carries the full, untruncated string. Checking only `hasVisualOverflow` cannot show it
     * either: overflow is expected and correct here, it just must not have eaten the size yet. So
     * this asserts against the LAID-OUT result's own idea of what is visible:
     * `getLineEnd(lastLine, visibleEnd = true)` returns the offset of the last character actually
     * painted (excluding any ellipsis), and the size's character range must fall entirely before
     * that offset.
     */
    @Test fun a_narrow_row_still_paints_the_install_size_before_the_truncation_point() {
        setRow(row(recommended = true), widthDp = 280)
        val layout = subtitleLayout()
        val full = layout.layoutInput.text.text
        val sizeIndex = full.indexOf("4.2 MB")
        assertTrue(sizeIndex >= 0, "subtitle text should contain the install size, was: '$full'")
        val lastVisibleOffset = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
        assertTrue(
            sizeIndex + "4.2 MB".length <= lastVisibleOffset,
            "the install size ('4.2 MB' at [$sizeIndex, ${sizeIndex + "4.2 MB".length})) must be " +
                "fully painted before the truncation point (last visible offset $lastVisibleOffset), " +
                "full text: '$full'",
        )
    }

    @Test fun a_bad_document_shows_the_warning_outside_selection_mode() {
        setRow(row(badWarn = true))
        compose.onNodeWithContentDescription(badWarnText).assertIsDisplayed()
    }

    @Test fun the_bad_document_warning_survives_selection_mode_because_it_is_safety_information() {
        setRow(row(badWarn = true), selectionMode = true)
        compose.onNodeWithContentDescription(badWarnText).assertIsDisplayed()
    }

    /**
     * The laid-out subtitle (the node carrying the "Recommended!" caption). The UNMERGED tree is
     * mandatory: the row's `combinedClickable` merges its descendants, so a merged-tree query
     * returns the whole row — whose GetTextLayoutResult action is the title's, not the subtitle's.
     */
    private fun subtitleLayout(): TextLayoutResult =
        compose.onNodeWithText(recommendedText, substring = true, useUnmergedTree = true).textLayout()

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }
}
