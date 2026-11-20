package net.bible.sharedui.components

import androidx.compose.runtime.Composable

/**
 * Makes the dialog window this is composed in edge-to-edge (it also covers the status and navigation bar areas, so
 * the content must pad itself with the safe-drawing insets) and sets the system bar icons dark ([darkIcons]) or light
 * to contrast with the page behind them. Used by the full-screen text page; a no-op where there are no system bars.
 * Must be composed inside a dialog's content.
 */
@Composable
expect fun DialogEdgeToEdge(darkIcons: Boolean)
