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

package net.bible.sharedui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsItem

/**
 * A single-choice list as a modal bottom sheet — the sheet form of [AbListChoiceDialog] (round 14a,
 * spec §3 group 2 / §4). Its parameter list is that dialog's plus a hoisted [open], so a conversion
 * is a name change at the call site.
 *
 * Open state is hoisted and the composable returns early when closed, following
 * `SearchSettingsSheet.kt:41-42`. [skipPartiallyExpanded] is `true` (spec §7.a): the sheet opens
 * directly at its content height, so there is no "I dragged it to the maximum, this must be all"
 * state — which is the trap complaint 4 described.
 *
 * DISMISS MEANS CLOSE here, so there is deliberately NO `LaunchedEffect(sheetState.isVisible)`
 * re-show. Material3 does run `hide()` before invoking `onDismissRequest`, and
 * `SpeakSettingsSheet.kt:81-90` carries such an effect — but only because ITS dismiss can step back
 * a page without changing what is shown, leaving the sheet composed and invisible. Nothing here has
 * a page stack: `onDismiss` clears the state that gated [open] and this composable leaves the tree.
 * Copying that effect into this file would fight the close.
 *
 * ROBORAZZI: never capture this composable — an open `ModalBottomSheet` hangs the capture and takes
 * the whole `:app` suite with it. Capture [AbChoiceSheetContent] in a plain `Surface` instead;
 * `SettingsEditorSheetGuardTest.noGoldenTestCapturesSettingsEditorSheet` enforces it by name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbChoiceSheet(
    open: Boolean,
    title: String,
    choices: List<SettingsItem.Choice>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AbModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AbChoiceSheetContent(
            title = title,
            choices = choices,
            selectedValue = selectedValue,
            // Fires onSelect AND onDismiss synchronously on a tap, exactly as
            // `AbListChoiceDialog.kt:112` does — this is a CONTRACT, not an accident. Two hosts
            // depend on it: `AiModelsComposeActivity.kt:59-61` and `AiProvidersComposeActivity.kt`
            // each keep a `swallowNextDismiss` flag that consumes precisely one spurious dismiss per
            // pick, so that the pick's own step transition is not clobbered. Dropping the dismiss
            // here, or firing it first, would leave those flags to swallow a REAL dismiss and the
            // affected pickers would stop closing.
            onSelect = { onSelect(it); onDismiss() },
            onClose = onDismiss,
        )
    }
}

/**
 * [AbChoiceSheet]'s body: header, then the radio rows inside the bounded, faded scroll region.
 * Stateless — it never dismisses anything itself, which is what makes it capturable.
 */
@Composable
fun AbChoiceSheetContent(
    title: String,
    choices: List<SettingsItem.Choice>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = title, onClose = onClose)
        AbSheetScrollBound(canScrollForward = { scrollState.canScrollForward }) {
            AbListChoiceContent(
                choices = choices,
                selectedValue = selectedValue,
                onSelect = onSelect,
                // Horizontal padding only. `AbListChoiceContent`'s CAUTION kdoc is about a HEIGHT
                // bound landing inside its `verticalScroll` — padding is unaffected by that
                // mechanism, and the height bound is supplied by `AbSheetScrollBound` above,
                // exactly as that kdoc requires.
                modifier = Modifier.padding(horizontal = 16.dp),
                scrollState = scrollState,
            )
        }
    }
}
