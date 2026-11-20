package net.bible.sharedui.components

import androidx.compose.runtime.Composable

/**
 * Edge-to-edge dialog windows (the full-screen text page) draw under the system bars with their own icon colours,
 * which need not match the page. Sets the status and navigation bar icons dark ([darkIcons]) or light for the
 * dialog window this is composed in; a no-op where there are no system bars. Must be composed inside a dialog's content.
 */
@Composable
expect fun DialogSystemBarIcons(darkIcons: Boolean)
