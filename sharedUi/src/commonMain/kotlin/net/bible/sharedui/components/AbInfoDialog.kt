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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/**
 * Scrollable, height-bounded informational dialog: a title + a plain-text body + a single dismiss
 * button. Used for help text, disclaimers, and tool descriptions (F28/F30/F34/F37) where the body
 * may be long — the body is capped at [maxBodyHeight] and scrolls internally instead of growing the
 * dialog past the screen. Any HTML in the source text must be flattened to plain text by the caller
 * before it reaches [body]; this composable renders it verbatim (newlines preserved).
 *
 * When both [readMoreLabel] and [readMoreUrl] are non-null, an underlined, theme-tinted "read more"
 * link is rendered below the (still height-bounded/scrollable) body; tapping it opens [readMoreUrl]
 * via [LocalUriHandler] (Compose Multiplatform's built-in, iOS-clean URL opener — see
 * `AiProvidersScreen`/`EasySetupWizard` for prior usage). Underlining (not just a tinted color) keeps
 * the affordance legible in monochrome/e-ink theme modes. Since round 15a the scroll/height bound
 * sits on the whole [Column] (body + [content] + the "read more" link) rather than on the body
 * [Text] alone, so the "read more" link now scrolls WITH the content instead of staying pinned below
 * it — with a slot below the body, bounding only the body would let a long legend push the buttons
 * off screen.
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
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                        modifier = Modifier.clickable { uriHandler.openUri(readMoreUrl) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(confirmLabel) } },
    )
}

/** Cap on the scrollable body's height, so a long help/disclaimer text scrolls instead of growing
 *  the dialog beyond the screen. */
private val maxBodyHeight = 420.dp
