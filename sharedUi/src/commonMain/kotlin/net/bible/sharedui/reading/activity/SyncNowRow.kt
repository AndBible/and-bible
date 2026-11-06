@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.activity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.bible.sharedui.components.abMenuItemColors
import net.bible.sharedui.strings.LocalStrings

@Composable
internal fun SyncNowRow(state: SyncNowMenuState, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(LocalStrings.current.syncNow) },
        onClick = onClick,
        enabled = !state.running.value,
        leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
        modifier = Modifier.testTag(SYNC_NOW_ROW_TAG),
        colors = abMenuItemColors(),
    )
}
