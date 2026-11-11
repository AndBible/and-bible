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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedcore.ui.dialog.ReleaseNoticeAction
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome

/** The palette of the card: andbible.org's warm cream/amber (`website/assets/css/site.css`), or plain surface/onSurface in monochrome. */
private class NoticePalette(
    val panel: Brush, val ink: Color, val muted: Color, val border: Color, val tile: Color,
    val accent: Color, val accentInk: Color, val highlight: Color, val buttonBg: Color, val buttonInk: Color,
)

@Composable
private fun noticePalette(): NoticePalette {
    val dark = LocalIsDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    return when {
        isPureMonochrome() -> NoticePalette(
            panel = Brush.verticalGradient(listOf(scheme.surface, scheme.surface)), ink = scheme.onSurface,
            muted = scheme.onSurface, border = Color.Gray, tile = scheme.surface, accent = scheme.onSurface,
            accentInk = scheme.onSurface, highlight = scheme.surface, buttonBg = scheme.onSurface, buttonInk = scheme.surface,
        )
        dark -> NoticePalette(
            panel = Brush.linearGradient(listOf(Color(0xFF22211F), Color(0xFF1A1A19), Color(0xFF141414))),
            ink = Color(0xFFEFE9DD), muted = Color(0xFFA59F93), border = Color(0xFF2C2A26), tile = Color(0xFF121212),
            accent = Color(0xFFFEBA2A), accentInk = Color(0xFFFEBA2A), highlight = Color(0xFF2A2415),
            buttonBg = Color(0xFFEFE9DD), buttonInk = Color(0xFF161616),
        )
        else -> NoticePalette(
            panel = Brush.linearGradient(listOf(Color(0xFFFFFDF6), Color(0xFFFFFAF0), Color(0xFFF6EEDC))),
            ink = Color(0xFF2A2620), muted = Color(0xFF5D5546), border = Color(0xFFE3D8C0), tile = Color(0xFFF3EAD7),
            accent = Color(0xFFD99A2B), accentInk = Color(0xFF9A5B10), highlight = Color(0xFFFFF3D6),
            buttonBg = Color(0xFF2A2620), buttonInk = Color(0xFFF7F1E3),
        )
    }
}

/**
 * The 6.0 stable release notice: a card over the current screen with a header, three action rows, a
 * sponsor (or, with [sponsorAllowed] false, a website) pill and a footer with "Don't show again" and Close.
 * Every way out calls [onAnswer] once with the chosen action (null for Close, back and scrim) and the
 * checkbox state. The footer sits outside the scrolling body so Close stays reachable at large font sizes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbReleaseNoticeCard(
    version: String,
    sponsorAllowed: Boolean,
    logoPainter: Painter?,
    onAnswer: (ReleaseNoticeAction?, Boolean) -> Unit,
) {
    TrackOverlay()
    val strings = LocalStrings.current
    val mono = isPureMonochrome()
    val palette = noticePalette()
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Dialog(
        onDismissRequest = { onAnswer(null, dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (mono) NoDialogDim()
        Column(
            modifier = Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth()
                .clip(shape).background(palette.panel).border(1.dp, if (mono) palette.ink else palette.border, shape),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (logoPainter != null) {
                    Image(
                        logoPainter, contentDescription = null, modifier = Modifier.size(52.dp),
                        colorFilter = if (mono) ColorFilter.tint(palette.ink) else null,
                    )
                }
                Text(
                    strings.releaseNoticeKicker(version).uppercase(),
                    color = palette.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    buildAnnotatedString {
                        append(strings.releaseNoticeHeadlineStart)
                        append(" ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = palette.accentInk)) {
                            append(strings.releaseNoticeHeadlineAccent)
                        }
                    },
                    color = palette.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
                    lineHeight = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Android", strings.releaseNoticePlatformIphoneIpad, "Windows", "Mac", "Linux").forEach {
                        PlatformChip(it, palette)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionRow(
                        Icons.Filled.PlayArrow, strings.releaseNoticeVideoTitle(version), strings.releaseNoticeVideoSubtitle,
                        palette, highlighted = true,
                    ) { onAnswer(ReleaseNoticeAction.VIDEO, dontShowAgain) }
                    ActionRow(
                        Icons.Filled.Computer, strings.releaseNoticeComputerTitle, strings.releaseNoticeComputerSubtitle, palette,
                    ) { onAnswer(ReleaseNoticeAction.COMPUTER, dontShowAgain) }
                    ActionRow(
                        Icons.Filled.Share, strings.releaseNoticeShareTitle, strings.releaseNoticeShareSubtitle, palette,
                    ) { onAnswer(ReleaseNoticeAction.SHARE, dontShowAgain) }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.clip(CircleShape).border(1.dp, if (mono) palette.ink else palette.accent, CircleShape)
                        .clickable(role = Role.Button) {
                            onAnswer(if (sponsorAllowed) ReleaseNoticeAction.SPONSOR else ReleaseNoticeAction.WEBSITE, dontShowAgain)
                        }
                        .heightIn(min = 40.dp).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sponsorAllowed) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        if (sponsorAllowed) strings.releaseNoticeSponsor else strings.releaseNoticeWebsite,
                        color = palette.accentInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                }
                Text(
                    strings.releaseNoticeFreeNoAds, color = palette.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .toggleable(value = dontShowAgain, role = Role.Checkbox) { dontShowAgain = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = null)
                    Text(
                        strings.releaseNoticeDontShowAgain, color = palette.muted, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    )
                }
                Button(
                    onClick = { onAnswer(null, dontShowAgain) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.buttonBg, contentColor = palette.buttonInk),
                ) { Text(strings.settingsEditorClose, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PlatformChip(label: String, palette: NoticePalette) {
    Text(
        label, color = palette.muted, fontSize = 11.sp,
        modifier = Modifier.border(1.dp, palette.border, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector, title: String, subtitle: String, palette: NoticePalette,
    highlighted: Boolean = false, onClick: () -> Unit,
) {
    val shape: Shape = RoundedCornerShape(12.dp)
    val mono = isPureMonochrome()
    val border = if (highlighted && !mono) BorderStroke(1.dp, palette.accent) else BorderStroke(1.dp, palette.border)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (highlighted) palette.highlight else Color.Transparent).border(border, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.tile)
                .then(if (mono) Modifier.border(1.dp, palette.border, RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(20.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = palette.muted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.muted, modifier = Modifier.size(20.dp))
    }
}
