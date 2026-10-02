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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "create a new item" affordance as a bottom sheet, with the sibling "import instead" action in
 * the same sheet — one ＋ toolbar icon therefore covers both ways of adding content, and no overflow
 * menu is needed for either.
 *
 * Split into [AbCreateItemSheet] (the `ModalBottomSheet` wrapper, owning dismissal) and
 * [AbCreateItemSheetContent] (the body) so a golden test can capture the body directly — a
 * `ModalBottomSheet`'s entrance animation makes a capture flaky. Same split as [LabelIdentitySheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbCreateItemSheet(
    title: String,
    initialName: String,
    confirmText: String,
    importText: String,
    onCreate: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AbModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AbCreateItemSheetContent(
            title = title,
            initialName = initialName,
            confirmText = confirmText,
            importText = importText,
            onCreate = onCreate,
            onImport = onImport,
            extraContent = extraContent,
        )
    }
}

@Composable
fun AbCreateItemSheetContent(
    title: String,
    initialName: String,
    confirmText: String,
    importText: String,
    onCreate: (String) -> Unit,
    onImport: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    // Mirrors AbTextInputContent's own contract: it owns the text and reports every keystroke, which
    // is what lets this sheet disable its confirm button on a blank name (the dialog it replaces
    // silently swallowed a blank confirm instead).
    var current by remember(initialName) { mutableStateOf(initialName) }
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        AbTextInputContent(
            initial = initialName,
            onValueChange = { current = it },
            extraContent = extraContent,
        )
        Button(
            onClick = { onCreate(current.trim()) },
            enabled = current.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text(confirmText) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onImport).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
            Text(importText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
