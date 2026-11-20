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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.strings.LocalStrings

/**
 * Scrollable, height-bounded informational dialog on [AbDialogShell]: [title] as the lead line, a plain-text
 * body and a single dismiss button. Used for help text, disclaimers, and tool descriptions (F28/F30/F34/F37)
 * where the body may be long — the shell's middle area is bounded by the window and scrolls (with dividers at
 * the edges that can scroll further) instead of growing the dialog past the screen. Any HTML in the source text must be flattened to plain text by the caller
 * before it reaches [body]; this composable renders it verbatim (newlines preserved).
 *
 * When both [readMoreLabel] and [readMoreUrl] are non-null, an underlined, theme-tinted "read more"
 * link is rendered below the (still height-bounded/scrollable) body; tapping it opens [readMoreUrl]
 * through [LocalAbLinkOpener] when an enclosing [AbLinkRouting] provides one (so the discrete build's
 * "External link?" question applies), else falls back to [LocalUriHandler]. The dialog window
 * re-provides a bare platform [LocalUriHandler] that shadows the host's override (C1), so the
 * custom local is the only one that reaches the click. Underlining (not just a tinted color) keeps
 * the affordance legible in monochrome/e-ink theme modes. The body, the [content] slot and the
 * "read more" link all scroll together in the shell's middle area, so a long legend can never push the
 * buttons off screen.
 *
 * [content] is an optional slot rendered between the body and the "read more" link, for a dialog
 * whose content is not plain prose — round 15a's labels help is an icon legend, where each row is
 * the real Compose icon the screen draws next to one short sentence. A blank [body] is allowed so
 * such a dialog can be all slot.
 */
@Composable
fun AbInfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    confirmLabel: String = LocalStrings.current.okay,
    readMoreLabel: String? = null,
    readMoreUrl: String? = null,
    /** The help topic of the "Help in the user guide" link (#4159): opens Help & tips at the topic's guide page. */
    helpTopic: net.bible.sharedcore.docs.DocsLink? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val open = rememberLinkOpener()
    AbDialogShell(
        onDismissRequest = onDismiss,
        lead = { Text(title) },
        content = {
            // A blank body is legitimate: a dialog whose whole content is [content] (an icon
            // legend, say) has nothing to put here, and an empty Text would still take a line.
            if (body.isNotBlank()) Text(text = body)
            content?.invoke(this)
            if (readMoreLabel != null && readMoreUrl != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = readMoreLabel,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    // A help link (#4159) closes this dialog first: Help & tips opens under the dialog otherwise.
                    modifier = Modifier.clickable {
                        if (net.bible.sharedcore.docs.HelpLinks.isHelpUrl(readMoreUrl)) onDismiss()
                        open(readMoreUrl)
                    },
                )
            }
            if (helpTopic != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = LocalStrings.current.helpInUserGuide,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onDismiss(); open(net.bible.sharedcore.docs.HelpLinks.url(helpTopic)) },
                )
            }
        },
        buttons = { AbDialogButton(confirmLabel, DialogButtonRole.Confirm, onDismiss) },
    )
}
