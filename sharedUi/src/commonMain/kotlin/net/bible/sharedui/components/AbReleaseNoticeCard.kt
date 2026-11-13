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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.ui.dialog.ReleaseNoticeAction
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.isPureMonochrome

/** The colours of the release notice card; [glow] is the radial tint behind the panel (null: a flat panel). */
internal class NoticePalette(
    val panel: Color, val glow: Color?, val ink: Color, val muted: Color, val border: Color, val tile: Color,
    val accent: Color, val accentInk: Color, val highlight: Color, val highlightInk: Color,
    val buttonBg: Color, val buttonInk: Color,
)

/** Every e-ink/BW mode is a greyscale mode: no hues and no gradient on the card. */
@Composable
private fun isGreyMode(): Boolean = LocalDisplayColorMode.current.isGreyBase

/**
 * The card's colours for [scheme] in [mode]: the grey-base modes (BW, MONOCHROME, COLOR_EINK) are flat
 * surface/onSurface; NORMAL follows the theme and draws the glow.
 */
internal fun releaseNoticePalette(scheme: ColorScheme, mode: DisplayColorMode): NoticePalette = when {
    mode.isGreyBase -> NoticePalette(
        panel = scheme.surface, glow = null, ink = scheme.onSurface,
        muted = scheme.onSurface, border = Color.Gray, tile = scheme.surface, accent = scheme.onSurface,
        accentInk = scheme.onSurface, highlight = scheme.surface, highlightInk = scheme.onSurface,
        buttonBg = scheme.onSurface, buttonInk = scheme.surface,
    )
    // Light and dark: the app theme, like every other dialog (#4064); the primary glow keeps the mockup's warmth.
    else -> NoticePalette(
        panel = scheme.surfaceContainerHigh, glow = scheme.primary.copy(alpha = NOTICE_GLOW_ALPHA),
        ink = scheme.onSurface, muted = scheme.onSurfaceVariant, border = scheme.outlineVariant,
        tile = scheme.surfaceContainerHighest, accent = scheme.primary, accentInk = scheme.primary,
        highlight = scheme.primaryContainer, highlightInk = scheme.onPrimaryContainer,
        buttonBg = scheme.primary, buttonInk = scheme.onPrimary,
    )
}

/** The alpha of the top-right glow; the bottom-left one is fainter. */
private const val NOTICE_GLOW_ALPHA = 0.16f

/**
 * The 6.0 stable release notice: a card over the current screen with a header, platform pills linking to their install instructions, three action rows, a
 * sponsor (or, with [sponsorAllowed] false, a website) pill and a footer with "Don't show again" and Close.
 * Every control calls [onAnswer] once with the chosen action (null for Close, back and scrim) and the
 * checkbox state, which starts at [initialDontShowAgain]. Only a null action ends the notice: for an action the
 * shared `ReleaseNotice` gate runs it and shows the card again with the checkbox carried over (#4063).
 * The footer sits outside the scrolling body so Close stays reachable at large font sizes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbReleaseNoticeCard(
    version: String,
    sponsorAllowed: Boolean,
    logoPainter: Painter?,
    computerAllowed: Boolean = true,
    initialDontShowAgain: Boolean = false,
    onAnswer: (ReleaseNoticeAction?, Boolean) -> Unit,
) {
    TrackOverlay()
    val strings = LocalStrings.current
    val mono = isGreyMode()
    val palette = releaseNoticePalette(MaterialTheme.colorScheme, LocalDisplayColorMode.current)
    // A Dialog window re-provides LocalDensity from its own owner; carry the host's density (font scale) in.
    val density = LocalDensity.current
    var dontShowAgain by rememberSaveable { mutableStateOf(initialDontShowAgain) }
    val shape = RoundedCornerShape(22.dp)
    Dialog(
        onDismissRequest = { onAnswer(null, dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (isPureMonochrome()) NoDialogDim()
        CompositionLocalProvider(LocalDensity provides density) {
            Column(
                modifier = Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth()
                    .clip(shape).background(palette.panel).noticeGlow(palette.glow).border(1.dp, if (mono) palette.ink else palette.border, shape),
            ) {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                        ) {
                            listOf(
                                "Android" to ReleaseNoticeAction.INSTALL_ANDROID,
                                strings.releaseNoticePlatformIphoneIpad to ReleaseNoticeAction.INSTALL_IOS,
                                "Windows" to ReleaseNoticeAction.INSTALL_WINDOWS,
                                "Mac" to ReleaseNoticeAction.INSTALL_MAC,
                                "Linux" to ReleaseNoticeAction.INSTALL_LINUX,
                            ).forEach { (label, action) ->
                                PlatformPill(label, strings.releaseNoticePlatformInstall, palette) { onAnswer(action, dontShowAgain) }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionRow(
                                Icons.Filled.PlayArrow, strings.releaseNoticeVideoTitle(version), strings.releaseNoticeVideoSubtitle,
                                palette, highlighted = true,
                            ) { onAnswer(ReleaseNoticeAction.VIDEO, dontShowAgain) }
                            if (computerAllowed) {
                                ActionRow(
                                    Icons.Filled.Computer, strings.releaseNoticeComputerTitle, strings.releaseNoticeComputerSubtitle, palette,
                                ) { onAnswer(ReleaseNoticeAction.COMPUTER, dontShowAgain) }
                            }
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
                                .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 8.dp),
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
                    // maxValue stays Int.MAX_VALUE until the first layout; without this guard the cue flashes on short content.
                    if (scrollState.maxValue != Int.MAX_VALUE && scrollState.canScrollForward) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(palette.panel).testTag("release-notice-scroll-cue"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(if (mono) palette.ink else palette.border))
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
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
}

/**
 * A platform pill (#4065): a link to that platform's install instructions. The visible pill stays small; the
 * clickable area around it is 48 dp tall, with a pill-shaped ripple.
 */
@Composable
private fun PlatformPill(label: String, clickLabel: String, palette: NoticePalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick).padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.border(1.dp, if (isGreyMode()) palette.ink else palette.border, CircleShape)
                .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = palette.accentInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = palette.accentInk,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector, title: String, subtitle: String, palette: NoticePalette,
    highlighted: Boolean = false, onClick: () -> Unit,
) {
    val shape: Shape = RoundedCornerShape(12.dp)
    val mono = isGreyMode()
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
            Text(title, color = if (highlighted) palette.highlightInk else palette.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = if (highlighted) palette.highlightInk else palette.muted, fontSize = 12.sp)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = if (highlighted) palette.highlightInk else palette.muted, modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The mockup's two corner glows (`specs/2026-11-11-stable-notice-6-0-mockup.html`): a radial tint of [glow] at the
 * top right and a fainter one at the bottom left. Null [glow] (monochrome / e-ink) draws nothing.
 */
private fun Modifier.noticeGlow(glow: Color?): Modifier = if (glow == null) this else drawBehind {
    val w = size.width
    drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset(w * 0.9f, 0f), radius = w * 0.7f))
    drawRect(
        Brush.radialGradient(
            listOf(glow.copy(alpha = glow.alpha * 0.45f), Color.Transparent),
            center = Offset(0f, size.height), radius = w * 0.6f,
        ),
    )
}
