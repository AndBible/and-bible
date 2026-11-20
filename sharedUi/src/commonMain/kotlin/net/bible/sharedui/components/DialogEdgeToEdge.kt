package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/**
 * Makes the dialog window this is composed in edge-to-edge (it also covers the status and navigation bar areas, so
 * the content must pad itself with the safe-drawing insets) and sets the system bar icons dark ([darkIcons]) or light
 * to contrast with the page behind them. Used by the full-screen text page; a no-op where there are no system bars.
 * Must be composed inside a dialog's content.
 */
@Composable
expect fun DialogEdgeToEdge(darkIcons: Boolean)

/**
 * The [DialogProperties] of the full-screen text page. On Android they ask for `decorFitsSystemWindows = false`
 * at construction, so the dialog window gets its edge-to-edge layout flags, cutout mode and inset fitting before
 * it is shown (setting it afterwards is not enough on older API levels). Elsewhere: platform-width off only.
 * The page is dismissed by Back only when [dismissOnBackPress]; never by a click outside.
 */
expect fun textPageDialogProperties(dismissOnBackPress: Boolean): DialogProperties
