package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun DialogEdgeToEdge(darkIcons: Boolean) {}

actual fun textPageDialogProperties(dismissOnBackPress: Boolean): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
)
