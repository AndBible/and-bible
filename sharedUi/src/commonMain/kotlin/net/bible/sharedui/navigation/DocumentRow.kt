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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow

/** Multiplatform-safe "%.1f MB" (no java String.format / no NumberFormat). */
private fun formatSizeMb(mb: Double): String {
    val tenths = (mb * 10.0).roundToInt()
    return "${tenths / 10}.${tenths % 10} MB"
}

/**
 * One row in the document-selection list. Renders a leading checkbox (selection mode) or a category
 * icon, a two-line title/subtitle, recommended/locked markers, and an install-status affordance.
 *
 * Modernization note: the classic red/green lock and status colours are intentionally dropped in
 * favour of [MaterialTheme.colorScheme] tints, so black-and-white / e-ink themes degrade automatically.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading: checkbox in selection mode, else the classic per-category icon (via the host
        // seam). The recommended star rides as a small badge on the leading icon's corner (classic
        // look), so it no longer costs a trailing slot.
        Box(contentAlignment = Alignment.Center) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Icon(
                    painter = LocalCategoryIcon.current(row.category),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (row.recommended) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp).align(Alignment.BottomEnd),
                )
            }
        }
        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${row.abbreviation} ${row.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildSubtitle(row, downloadMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Trailing markers: locked/enciphered (theme-tinted, not classic red/green). The recommended
        // star moved to the leading icon (see above).
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
    }
}

private fun buildSubtitle(row: DocRow, downloadMode: Boolean): String = buildString {
    append(row.language.displayName)
    if (row.repository.isNotEmpty()) {
        append(" · ")
        append(row.repository)
    }
    val sizeMb = row.installSizeMb
    if (downloadMode && sizeMb != null) {
        append(" · ")
        append(formatSizeMb(sizeMb))
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
                Icon(Icons.Filled.Close, contentDescription = null)
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
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                    }
                }
            DocInstallStatus.BEING_INSTALLED -> {} // handled above
        }
    }
}
