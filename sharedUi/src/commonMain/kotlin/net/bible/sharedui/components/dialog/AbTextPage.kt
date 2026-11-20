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


package net.bible.sharedui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.components.NoDialogDim
import net.bible.sharedui.components.TrackOverlay
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.monoInk

/** The text column of a page is never wider than this, and is centred on wide windows. */
private val PageMaxWidth = 720.dp

/**
 * A full-screen page for long text (disclaimers, licences): a top bar with ✕ and [topic], a scrolling [body] and a
 * footer. Use it instead of a dialog when the text is too long to read comfortably in one (spec §5.3).
 *
 *  - [confirmText] null: a read-only page, whose footer has a single filled "Close" (tag `dialog-dismiss`);
 *  - [confirmText] set: a decision page with [dismissText] (required) and the filled [confirmText] button, which is
 *    destructive when [destructive];
 *  - [onClose]: ✕, Back (when [cancellable]) and the footer's dismiss button;
 *  - a non-cancellable page hides ✕, and Back does nothing unless [onBack] is set.
 *
 * The text column is capped at 720dp and centred; the page keeps clear of the system bars.
 * In pure monochrome the page is paper with ink rules, with no platform dim.
 */
@Composable
fun AbTextPage(
    topic: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    destructive: Boolean = false,
    dismissText: String? = null,
    cancellable: Boolean = true,
    onBack: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    TrackOverlay()
    val strings = LocalStrings.current
    val mono = isPureMonochrome()
    Dialog(
        onDismissRequest = { if (cancellable) onClose() else onBack?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = cancellable || onBack != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        if (mono) NoDialogDim()
        val rule = if (mono) monoInk(LocalIsDarkTheme.current) else MaterialTheme.colorScheme.outlineVariant
        Surface(
            modifier = modifier.fillMaxSize().testTag("text-page"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (mono) 0.dp else 3.dp,
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cancellable) {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("page-close")) {
                            Icon(Icons.Filled.Close, contentDescription = strings.settingsEditorClose)
                        }
                    }
                    Text(
                        topic,
                        modifier = Modifier.weight(1f).padding(start = if (cancellable) 4.dp else 16.dp, end = 16.dp)
                            .testTag("page-topic").semantics { heading() },
                        style = abDialogLeadStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(color = rule)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = AbDialogTokens.PaddingSide, vertical = 16.dp),
                    ) {
                        androidx.compose.material3.ProvideTextStyle(abDialogBodyStyle()) { body() }
                    }
                }
                HorizontalDivider(color = rule)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (confirmText == null) {
                        AbDialogButton(strings.settingsEditorClose, DialogButtonRole.Cancel, onClose, filled = true)
                    } else {
                        if (dismissText != null) AbDialogButton(dismissText, DialogButtonRole.Cancel, onClose)
                        AbDialogButton(
                            confirmText, if (destructive) DialogButtonRole.Destructive else DialogButtonRole.Confirm,
                            onConfirm, filled = true,
                        )
                    }
                }
            }
        }
    }
}
