package net.bible.sharedui.components

import androidx.compose.runtime.Composable

/** Temporarily suppress the platform scrim; must be composed inside a dialog's content. */
@Composable
expect fun NoDialogDim()
