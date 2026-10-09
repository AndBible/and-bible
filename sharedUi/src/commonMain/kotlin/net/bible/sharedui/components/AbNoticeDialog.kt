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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ui.dialog.AppDialogRequest

/**
 * The two platform drawables a [AppDialogRequest.Notice] can draw — [logo] for
 * [AppDialogRequest.Notice.showTitleLogo]/[AppDialogRequest.NoticeBlock.Logo], [money] for
 * [AppDialogRequest.NoticeBlock.IconLine]'s [AppDialogRequest.NoticeIcon.Money]. `:sharedUi` stays
 * resource-free (spec constraint): the real painters (`painterResource(R.drawable.ic_logo)`,
 * `painterResource(R.drawable.baseline_attach_money_24)`) are supplied by `:app`'s
 * `AppDialogOverlay` via [LocalNoticeIcons]; a golden or a compose-ui-test supplies stand-ins
 * directly to [AbNoticeDialog] instead of going through the composition local.
 */
data class NoticeIcons(val logo: Painter, val money: Painter)

/** Provided by `:app`'s `AppDialogOverlay`. Null in any render path that never installs it (a bare
 *  preview, a test that constructs [AbNoticeDialog] directly): the icon is then simply not drawn. */
val LocalNoticeIcons = staticCompositionLocalOf<NoticeIcons?> { null }

/**
 * The replacement for a platform `AlertDialog.Builder` that also called `setIcon` and/or embedded an
 * `ImageSpan` in its message (`ReadingAppBootstrap.showStableNotice`/`showBetaNotice`,
 * `CommonUtils.showHelp`). [blocks] render top-down, each block a paragraph of its own — the Column's
 * own spacing separates them, so a block's HTML carries no leading/trailing `<br><br>` of its own
 * (spec §6.1 "images are not the converter's job").
 *
 * Buttons follow [AbMessageDialog]'s mapping: [confirmText] is the M3 `confirmButton` (`Ok`),
 * [dismissText]/[neutralText] the `dismissButton` row (`Cancel`/`Neutral`) — same layout as
 * `AbMessageDialog`. There is no `cancellable` parameter: every one of today's three notices leaves
 * its platform dialog at the default `setCancelable(true)`, so back/scrim always answers
 * [onDismissRequest] here, exactly as [AbMessageDialog]'s own default does.
 */
@Composable
fun AbNoticeDialog(
    title: String?,
    showTitleLogo: Boolean,
    blocks: List<AppDialogRequest.NoticeBlock>,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    neutralText: String? = null,
    onNeutral: () -> Unit = {},
    logoPainter: Painter? = null,
    moneyPainter: Painter? = null,
) {
    AbAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = if (showTitleLogo && logoPainter != null) {
            { Image(logoPainter, contentDescription = null, modifier = Modifier.size(32.dp)) }
        } else null,
        title = if (title != null) { { Text(title) } } else null,
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                blocks.forEach { block ->
                    when (block) {
                        is AppDialogRequest.NoticeBlock.Html -> AbHtmlText(block.html)
                        AppDialogRequest.NoticeBlock.Logo ->
                            if (logoPainter != null) {
                                Image(
                                    logoPainter, contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).size(96.dp),
                                )
                            }
                        is AppDialogRequest.NoticeBlock.IconLine -> {
                            val iconPainter = when (block.icon) {
                                AppDialogRequest.NoticeIcon.Money -> moneyPainter
                            }
                            AbHtmlText(
                                block.html, leadingIcon = iconPainter,
                                leadingIconTint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = if (dismissText != null || neutralText != null) {
            {
                Row {
                    if (neutralText != null) TextButton(onClick = onNeutral) { Text(neutralText) }
                    if (dismissText != null) TextButton(onClick = onDismiss) { Text(dismissText) }
                }
            }
        } else null,
    )
}
