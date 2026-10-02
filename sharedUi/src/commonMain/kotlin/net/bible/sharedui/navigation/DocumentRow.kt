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
package net.bible.sharedui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.components.AbDocumentListRow
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisplayColorMode

/** Multiplatform-safe "%.1f MB" (no java String.format / no NumberFormat). */
private fun formatSizeMb(mb: Double): String {
    val tenths = (mb * 10.0).roundToInt()
    return "${tenths / 10}.${tenths % 10} MB"
}

/** Classic `@color/yellow_600` (`document_list_item.xml`'s recommendedIcon tint). */
private val RECOMMENDED_STAR_ARGB: Int = 0xFFFDD835.toInt()

/**
 * One row in the document-selection list. Renders a leading checkbox (selection mode) or a category
 * icon, a two-line title/subtitle, the recommended/bad-document/locked markers, and an
 * install-status affordance.
 *
 * Modernization note: the classic red/green lock and status colours are intentionally dropped in
 * favour of [MaterialTheme.colorScheme] tints, so black-and-white / e-ink themes degrade automatically.
 *
 * Built on the shared [AbDocumentListRow] anatomy (extracted round 17e-2); this function supplies
 * only the document-specific leading/trailing content.
 */
@Composable
fun DocumentRow(
    row: DocRow,
    downloadMode: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    AbDocumentListRow(
        title = "${row.abbreviation} ${row.name}",
        subtitle = buildSubtitle(row, downloadMode, strings.recommendedDocument),
        onClick = onClick,
        onLongClick = onLongClick,
        // TWO lines, because the bold "Recommended!" caption prefixes this line in download
        // mode and one line then ellipsized away both the repository and the install size --
        // the one number a download decision needs, missing on exactly the documents the
        // user is most likely to choose. A non-recommended row still fits one line.
        subtitleMaxLines = 2,
        leading = {
            // Leading: checkbox in selection mode, else the classic per-category icon (via the host
            // seam) with the "recommended" star as a BADGE in its bottom-right corner.
            //
            // The badge position was tried once before (4f7b8774f) and reverted for two reasons, both
            // answered here. It dimmed the star against the icon it overlapped -- so the star now sits
            // on a background-coloured halo (a filled circle one third larger than the glyph), which
            // separates it from whatever it covers in every theme. And in selection mode it landed on
            // top of the checkbox -- so, as before, the star is simply not drawn there: it is
            // decoration, and selection mode has no room for it.
            //
            // Why a badge at all: as an inline marker it pushed the whole text column 22dp to the
            // right on recommended rows only, so the list's text edge jittered down the page. A badge
            // costs no horizontal space, so the text column starts at the same x on every row.
            //
            // The bad-document marker (badWarn) sits BEFORE the text column, not after it -- kept
            // here as a sibling of the icon/checkbox box rather than moved to the trailing slot, so
            // this extraction doesn't shift it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    if (selectionMode) {
                        Checkbox(checked = selected, onCheckedChange = null)
                    } else {
                        Icon(
                            painter = LocalCategoryIcon.current(row.category),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        if (row.recommended) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 3.dp, y = 3.dp)
                                    .size(14.dp)
                                    .background(MaterialTheme.colorScheme.background, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = strings.recommendedDocument,
                                    // accentArgbFor keeps classic's amber in the normal and COLOR_EINK
                                    // modes and greys it on BW/monochrome, like the download arrow below.
                                    tint = Color(accentArgbFor(RECOMMENDED_STAR_ARGB, LocalDisplayColorMode.current)),
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }
                }
                if (row.badWarn) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.ThumbDown,
                        contentDescription = strings.badDocumentWarning,
                        // colorScheme.error, not classic's hardcoded red, per this file's standing
                        // modernization note -- so BW/e-ink degrade automatically.
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        trailing = {
            // Trailing markers: locked/enciphered (theme-tinted, not classic red/green).
            if (row.locked || row.enciphered) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(8.dp))
            InstallAffordance(row, downloadMode, onDownload, onCancel)
        },
    )
}

/**
 * The subtitle line. A recommended document leads with classic's bold "Recommended!" caption
 * (`recommendedString` in `document_list_item.xml`, which the port had dropped) — as a prefix
 * rather than classic's own dedicated line, so it costs no row height. The caption stays in
 * selection mode: it is text, and unlike the star marker nothing can overlap it.
 *
 * Field order in download mode is `[caption ·] size · language · repository` — deliberately NOT
 * alphabetical or "most specific last". With `maxLines = 2` the line still ellipsizes on a narrow
 * phone (measured: at 320dp the subtitle column is 176dp wide and overflows even at two lines;
 * 360dp is the first width that fits), so the field ORDER decides what survives truncation, not
 * just what is technically present. The install size is the one number a download decision
 * needs, so it goes first among the data fields (right after the caption); the repository is the
 * least decision-relevant field, so it is placed last and is the one sacrificed when space runs
 * out. Non-download mode (Choose-documents) has no size at all and stays `language · repository`.
 */
private fun buildSubtitle(
    row: DocRow,
    downloadMode: Boolean,
    recommendedCaption: String,
): AnnotatedString = buildAnnotatedString {
    if (row.recommended) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(recommendedCaption) }
        append(" · ")
    }
    val sizeMb = row.installSizeMb
    if (downloadMode && sizeMb != null) {
        append(formatSizeMb(sizeMb))
        append(" · ")
    }
    append(row.language.displayName)
    if (row.repository.isNotEmpty()) {
        append(" · ")
        append(row.repository)
    }
}

/**
 * The trailing status affordance, driven by [DocRow.installStatus].
 *
 * Every single-mark state renders inside a fixed 48dp slot (the same footprint as the download
 * [IconButton]'s touch target) so the trailing marks line up down the list regardless of state.
 * BEING_INSTALLED is the exception: it shows a progress bar plus a cancel button.
 */
@Composable
private fun InstallAffordance(
    row: DocRow,
    downloadMode: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    if (row.installStatus == DocInstallStatus.BEING_INSTALLED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { row.percentDone / 100f },
                modifier = Modifier.width(64.dp),
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = LocalStrings.current.cancel)
            }
        }
        return
    }
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (row.installStatus) {
            DocInstallStatus.INSTALLED ->
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            DocInstallStatus.UPGRADE_AVAILABLE ->
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            DocInstallStatus.ERROR_DOWNLOADING ->
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            DocInstallStatus.NOT_INSTALLED,
            DocInstallStatus.INSTALL_CANCELLED ->
                if (downloadMode) {
                    // Amber accent, grayed on BW/monochrome (kept coloured in normal + COLOR_EINK)
                    // via accentArgbFor + LocalDisplayColorMode, like the rest of this file.
                    val downloadTint = Color(accentArgbFor(0xFFFFC107.toInt(), LocalDisplayColorMode.current))
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = LocalStrings.current.cloudActionDownload,
                            tint = downloadTint,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            DocInstallStatus.BEING_INSTALLED -> {} // handled above
        }
    }
}
