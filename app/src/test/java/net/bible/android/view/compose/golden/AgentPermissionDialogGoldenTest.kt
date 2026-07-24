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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedui.ai.AgentPermissionChoiceRows
import net.bible.sharedui.ai.AgentPermissionDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Top-level dialogs ARE capturable by the harness (the force-open ban applies to
 * `DropdownMenu`/`Popup`/drawer sheets), so the primary states capture the real
 * [AgentPermissionDialog]. The `rows` state captures [AgentPermissionChoiceRows] in a plain
 * `Column` as well, so a regression in the five-button list is visible without dialog chrome.
 *
 * `heightDp = 700` on every capture: the dialog's message plus the five stacked
 * [AgentPermissionChoiceRows] buttons are NOT height-bounded or scrollable (unlike e.g. the
 * `AbInfoDialog` body), so a too-small capture viewport would silently clip the bottom button(s)
 * instead of failing loudly. 700dp gives headroom for the title, both message-line variants, and
 * all five buttons in every mode -- comparable to `AgentLogPanelGoldenTest`'s 520dp for a
 * similarly five-row (there: richer, more padded) panel.
 *
 * The [net.bible.sharedcore.ai.AgentPermissionChoice.ALLOW_ALWAYS] second confirmation is
 * deliberately NOT goldened here: that "are you sure" step lives in `AgentExecutor`'s existing
 * `Dialogs.simpleQuestion` call, not in this composable, so there is no state here to capture --
 * see [AgentPermissionDialog]'s own kdoc.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AgentPermissionDialogGoldenTest {

    private val withAction = AgentPermissionRequest(
        toolDisplayName = "Add bookmark",
        toolDescription = "Adds a bookmark to the current verse",
        actionDescription = "Bookmark John 3:16 with label \"Faith\"",
    )

    private val withoutAction = withAction.copy(actionDescription = null)

    private fun dialog(request: AgentPermissionRequest): @Composable () -> Unit = {
        AgentPermissionDialog(request = request, onChoice = {}, onDismiss = {})
    }

    @Test fun withAction_matrix() =
        captureMatrix("AgentPermissionDialog", "withAction", heightDp = 700, content = dialog(withAction))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun withAction_rtl() =
        captureRtl("AgentPermissionDialog", "withAction", heightDp = 700, content = dialog(withAction))

    @Test fun withoutAction() =
        captureGolden("AgentPermissionDialog", "withoutAction", EDGE_MODE, heightDp = 700, content = dialog(withoutAction))

    @Test fun rows() =
        captureGolden("AgentPermissionDialog", "rows", EDGE_MODE, heightDp = 700) {
            Column { AgentPermissionChoiceRows(toolDisplayName = "Add bookmark", onChoice = {}) }
        }
}
