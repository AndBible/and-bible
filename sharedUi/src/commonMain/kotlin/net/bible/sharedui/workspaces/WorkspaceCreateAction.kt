package net.bible.sharedui.workspaces

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.strings.LocalStrings

/** Shared New action and name dialog for workspace management and the quick selector. */
@Composable
fun WorkspaceCreateAction(workspaceCount: Int, onCreate: (String) -> Unit) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    AbActionIcon(Icons.Filled.Add, strings.newItem, { open = true })
    if (open) {
        AbTextInputDialog(
            title = strings.giveNameWorkspace,
            initial = strings.workspaceNumber(workspaceCount + 1),
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { name ->
                open = false
                if (name.isNotBlank()) onCreate(name.trim())
            },
            onDismiss = { open = false },
        )
    }
}
