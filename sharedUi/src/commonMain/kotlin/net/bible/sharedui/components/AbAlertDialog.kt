package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.components.dialog.AbDialogShell

/**
 * Adapter from the M3 `AlertDialog` signature to [AbDialogShell]: `title` is the lead line, `text` the
 * scrolling content, `dismissButton` then `confirmButton` the button row, `icon` the icon. The shell owns the
 * look (surface, border, monochrome handling) and the height safety (lead cap, scrolling middle).
 *
 * The shell's middle area scrolls [text]. Many callers still wrap their text in their own `verticalScroll`
 * (or use a lazy list), which Compose forbids under an infinite bound; [text] is therefore measured with a
 * finite, very large bound, so their inner scroll normally never engages and the shell's scroll does the work. Callers that cap
 * themselves (a `heightIn(max = ...)` list or picker) still scroll inside the shell's scrolling middle area;
 * those caps are redundant now and are for the copy batches to remove.
 */
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
    AbDialogShell(
        onDismissRequest = onDismissRequest,
        buttons = {
            dismissButton?.invoke()
            confirmButton()
        },
        modifier = modifier,
        icon = icon,
        lead = title,
        content = text?.let { body ->
            {
                // The shell's middle area scrolls this content, so it is measured with a finite (huge) bound:
                // callers whose content has its own scroll or a lazy list crash on an infinite one, and a
                // window-sized bound would clip plain content that is taller than the window.
                Box(Modifier.heightIn(max = 10000.dp)) { body() }
            }
        },
        properties = properties,
    )
}
