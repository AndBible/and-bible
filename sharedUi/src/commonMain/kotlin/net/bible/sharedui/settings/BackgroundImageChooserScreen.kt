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

package net.bible.sharedui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbLoadingOverlay
import net.bible.sharedui.components.AbScaffold

/**
 * The background-image picker sub-screen (opened from [ColorSettingsScreen]'s "Change" buttons): a
 * 2-column grid whose first two tiles are always **None** (clears the image) and **Import** (picks a
 * new one from the gallery), followed by one tile per installed background-image module. A short tap
 * selects; a long-press requests deleting that module (confirmed via [AbConfirmDialog]). While an
 * import is in flight, [AbLoadingOverlay] blocks the grid.
 *
 * [thumbnailFor] resolves a module's [BackgroundImageOption.thumbnailToken] to a decoded
 * [ImageBitmap] host-side (no android.graphics.* here) — `null` (not yet decoded, or decode failed)
 * falls back to a themed placeholder box so the grid never shows a broken tile.
 */
@Composable
fun BackgroundImageChooserScreen(
    options: List<BackgroundImageOption>,
    labels: BackgroundImageChooserLabels,
    loading: Boolean,
    deleteConfirm: BackgroundImageOption?,
    thumbnailFor: (token: String) -> ImageBitmap?,
    onUp: () -> Unit,
    onSelect: (initials: String?) -> Unit,
    onImport: () -> Unit,
    onRequestDelete: (BackgroundImageOption) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    AbScaffold(title = labels.title, onNavigateUp = onUp) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    item { FixedTile(text = labels.none, onClick = { onSelect(null) }) }
                    item { FixedTile(text = labels.import, onClick = onImport) }
                    items(options, key = { it.initials }) { option ->
                        BackgroundImageTile(
                            option = option,
                            thumbnail = thumbnailFor(option.thumbnailToken),
                            onClick = { onSelect(option.initials) },
                            onLongClick = { onRequestDelete(option) },
                        )
                    }
                    if (options.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = labels.empty,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                }
            }
            if (loading) AbLoadingOverlay(labels.importing)
        }
    }

    if (deleteConfirm != null) {
        AbConfirmDialog(
            title = labels.deleteTitle,
            message = labels.deleteConfirm,
            confirmText = labels.delete,
            dismissText = labels.cancel,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}

/** The always-present None / Import tiles: same square/label shape as [BackgroundImageTile], but
 *  plain text-only content and a single click action (no thumbnail, no long-press). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FixedTile(text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** One installed background-image module: a square thumbnail (or a themed placeholder box when
 *  [thumbnail] is `null`) plus its name below. Tap selects; long-press requests delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundImageTile(
    option: BackgroundImageOption,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it,
                    contentDescription = option.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = option.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
