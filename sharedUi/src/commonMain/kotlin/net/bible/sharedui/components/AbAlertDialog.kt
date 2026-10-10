package net.bible.sharedui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoBorder

/** M3 alert dialog, with an ink border, paper surface and no platform dim in MONOCHROME. */
@Composable
fun AbAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val mono = isPureMonochrome()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.monoBorder(AlertDialogDefaults.shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        // Keep a missing text slot missing outside mono: M3 uses slot presence for spacing.
        text = if (mono) {
            {
                NoDialogDim()
                text?.invoke()
                Unit
            }
        } else text,
        containerColor = if (mono) MaterialTheme.colorScheme.surface else AlertDialogDefaults.containerColor,
        tonalElevation = if (mono) 0.dp else AlertDialogDefaults.TonalElevation,
        properties = properties,
    )
}
