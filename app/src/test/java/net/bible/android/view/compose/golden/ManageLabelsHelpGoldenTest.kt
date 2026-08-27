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

    @Test fun help_assign() = captureMatrix("ManageLabelsHelp", "assign", heightDp = 700) {
        ManageLabelsHelpDialog(
            mode = ManageLabelsMode.ASSIGN,
            title = "Assign labels",
            scopeSentence = null,
            readMoreUrl = "https://example.invalid/",
            onDismiss = {},
        )
    }

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
