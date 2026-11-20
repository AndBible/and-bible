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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import net.bible.sharedui.generated.resources.Res
import net.bible.sharedui.generated.resources.andbible_qr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.abDialogBodyStyle
import net.bible.sharedui.theme.isPureMonochrome

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
 * Built on [AbDialogShell]: the title-logo goes in the shell's icon slot, [title] is the lead line and the
 * blocks scroll in the middle area in body style. Buttons follow [AbMessageDialog]'s order: neutral, dismiss,
 * then [confirmText] (Confirm role). There is no `cancellable` parameter: every one of today's three notices leaves
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
    val logoFilter = if (isPureMonochrome()) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
    AbDialogShell(
        onDismissRequest = onDismissRequest,
        icon = if (showTitleLogo && logoPainter != null) {
            { Image(logoPainter, contentDescription = null, modifier = Modifier.size(32.dp), colorFilter = logoFilter) }
        } else null,
        lead = title?.let { t -> { Text(t) } },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                blocks.forEach { block ->
                    when (block) {
                        is AppDialogRequest.NoticeBlock.Html -> AbHtmlText(block.html, style = abDialogBodyStyle())
                        AppDialogRequest.NoticeBlock.Logo ->
                            if (logoPainter != null) {
                                Image(
                                    logoPainter, contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).size(96.dp),
                                    colorFilter = logoFilter,
                                )
                            }
                        AppDialogRequest.NoticeBlock.QrCode ->
                            // Fixed black on white, whatever the theme: a themed or tinted code would not scan.
                            Image(
                                painterResource(Res.drawable.andbible_qr), contentDescription = null,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                    .size(220.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                            )
                        is AppDialogRequest.NoticeBlock.IconLine -> {
                            val iconPainter = when (block.icon) {
                                AppDialogRequest.NoticeIcon.Money -> moneyPainter
                            }
                            AbHtmlText(
                                block.html, style = abDialogBodyStyle(), leadingIcon = iconPainter,
                                leadingIconTint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        buttons = {
            if (neutralText != null) AbDialogButton(neutralText, DialogButtonRole.Neutral, onNeutral)
            if (dismissText != null) AbDialogButton(dismissText, DialogButtonRole.Cancel, onDismiss)
            AbDialogButton(confirmText, DialogButtonRole.Confirm, onConfirm)
        },
    )
}
