package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import net.bible.sharedui.strings.AndroidStrings
import net.bible.sharedui.strings.LocalStrings

/**
 * Provides the :sharedUi composition-locals with Android-backed impls. Every Activity that renders
 * moved :sharedUi composables must wrap content in this (inside AbTheme). LocalStrings defaults to
 * error(...), so a composable rendered without this crashes at render time.
 */
@Composable
fun ProvideAppLocals(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStrings provides AndroidStrings(LocalContext.current),
        content = content,
    )
}
