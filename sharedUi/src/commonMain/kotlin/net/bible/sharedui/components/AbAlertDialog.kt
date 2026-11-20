package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.components.dialog.AbDialogShell

/**
 * Adapter from the M3 `AlertDialog` signature to [AbDialogShell]: `title` is the lead line, `text` the
 * scrolling content, `dismissButton` then `confirmButton` the button row, `icon` the icon. The shell owns the
 * look (surface, border, monochrome handling) and the height safety (lead cap, scrolling middle).
 *
 * The shell's middle area scrolls, so it measures [text] with an unbounded height. Many callers still wrap
 * their text in their own `verticalScroll`, which Compose forbids under an infinite bound; [text] is therefore
 * bounded by the window height (a safety net: it only engages for taller-than-window content).
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
                val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                Box(Modifier.heightIn(max = windowHeight)) { body() }
            }
        },
        properties = properties,
    )
}
