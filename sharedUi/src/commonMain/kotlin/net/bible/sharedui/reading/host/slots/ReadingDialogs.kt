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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import net.bible.android.view.activity.page.screen.ReadingDialog
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbLinkRouting
import net.bible.sharedui.components.AbMessageDialog
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.Strings

/**
 * The reading view's own dialogs ([ReadingDialog]), an EIGHTH sibling overlay. Both arms are plain
 * `AlertDialog`-shaped (via `AbMessageDialog`/`AbConfirmDialog`), which stacks fine over any sheet
 * that happens to be open (`ReadingOverlayExclusion`'s kdoc), so this needs no exclusion wiring.
 *
 * A link in a [ReadingDialog.Help] body goes through [AbLinkRouting]: it asks first when
 * [ExternalLinks.askBeforeOpening] (discrete builds) and otherwise opens immediately.
 */
@kotlin.native.HiddenFromObjC
@Composable
internal fun ReadingDialogContent(hostState: ReadingHostState, strings: Strings, externalLinks: ExternalLinks) {
    when (val dialog = hostState.readingDialog.value) {
        null -> Unit
        is ReadingDialog.Help -> AbLinkRouting(
            askFirst = externalLinks.askBeforeOpening,
            onOpenExternal = { externalLinks.open(it) },
        ) {
            AbMessageDialog(
                title = dialog.title,
                html = dialog.html,
                confirmText = strings.okay,
                onConfirm = hostState::dismissReadingDialog,
                onDismissRequest = hostState::dismissReadingDialog,
            )
        }
        is ReadingDialog.ConfirmDeleteDocumentPage -> AbConfirmDialog(
            title = null,
            message = strings.aiDocumentDeleteConfirmation,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = hostState::confirmReadingDialog,
            onDismiss = hostState::dismissReadingDialog,
        )
    }
}
