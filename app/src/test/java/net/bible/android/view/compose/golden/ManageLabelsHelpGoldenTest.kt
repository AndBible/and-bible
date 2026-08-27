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
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedui.bookmark.ManageLabelsHelpDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The labels help dialog, one capture per [ManageLabelsMode]. The legend rows differ by mode
 * (ASSIGN gets all six rows per round 15a ruling R6; WORKSPACE five; HIDELABELS just the re-order
 * row; STUDYPAD has no legend at all), so each mode is its own state rather than one shared golden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ManageLabelsHelpGoldenTest {

    /**
     * ASSIGN, six legend rows -- of which this image shows FIVE. The sixth (re-order, the ⋮ glyph)
     * sits below the scroll fold, and no viewport height fixes that: [AbInfoDialog] caps its content
     * at its own max height and scrolls inside it, deliberately (round 15a Task 6 moved that bound
     * from the body Text to the whole Column precisely so a long legend cannot push the buttons off
     * screen). Raising heightDp to 1000 was tried and only re-centred the same-size dialog in more
     * grey.
     *
     * That is product behaviour, not a defect, and the legend is still fully covered by the four
     * captures together: the ⋮ row is the ONLY legend row in [help_hide], where it is plainly
     * visible. Whether a real user can reach it by scrolling is a device-pass item, not something a
     * static capture can answer.
     */
    @Test fun help_assign() = captureMatrix("ManageLabelsHelp", "assign", heightDp = 700) {
        ManageLabelsHelpDialog(
            mode = ManageLabelsMode.ASSIGN,
            title = "Assign labels",
            scopeSentence = null,
            readMoreUrl = "https://example.invalid/",
            onDismiss = {},
        )
    }

    /** WORKSPACE: the reused `auto_assing_labels_help1` intro plus the scope sentence, then five
     *  legend rows -- the last of which is below the scroll fold for the same designed reason as in
     *  [help_assign]. Read this image for the intro and scope sentence being the ORIGINAL translated
     *  strings rather than new English, which is the ≈54-translation saving §4.6 is about. */
    @Test fun help_workspace() = captureMatrix("ManageLabelsHelp", "workspace", heightDp = 700) {
        ManageLabelsHelpDialog(
            mode = ManageLabelsMode.WORKSPACE,
            title = "Labels",
            scopeSentence = "This setting applies to this workspace.",
            readMoreUrl = "https://example.invalid/",
            onDismiss = {},
        )
    }

    @Test fun help_hide() = captureGolden("ManageLabelsHelp", "hide", GoldenMode.LIGHT, heightDp = 700) {
        ManageLabelsHelpDialog(
            mode = ManageLabelsMode.HIDELABELS,
            title = "Hide specified labels",
            scopeSentence = "This setting applies to this workspace.",
            readMoreUrl = "https://example.invalid/",
            onDismiss = {},
        )
    }

    @Test fun help_studypad() = captureGolden("ManageLabelsHelp", "studypad", GoldenMode.LIGHT, heightDp = 700) {
        ManageLabelsHelpDialog(
            mode = ManageLabelsMode.STUDYPAD,
            title = "Study pads",
            scopeSentence = null,
            readMoreUrl = "https://example.invalid/",
            onDismiss = {},
        )
    }
}
