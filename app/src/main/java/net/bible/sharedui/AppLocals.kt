package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.settings.LocalSettingsIcon
import net.bible.sharedui.strings.AndroidStrings
import net.bible.sharedui.strings.LocalStrings

/**
 * Provides the :sharedUi composition-locals with Android-backed impls. Every Activity that renders
 * moved :sharedUi composables must wrap content in this (inside AbTheme). LocalStrings defaults to
 * error(...), so a composable rendered without this crashes at render time.
 *
 * [LocalCategoryIcon] is wired here (mapping each [net.bible.sharedcore.navigation.DocCategory] to
 * the classic vector drawable via [categoryDrawableRes]) so both the document-selection hosts AND
 * the Roborazzi golden harness — which wraps captures in this same provider — get the bespoke
 * per-category icons.
 *
 * [LocalSettingsIcon] is wired the same way, resolving a [net.bible.sharedcore.settings.SettingsItem]
 * `iconKey` to its classic drawable via [settingsDrawableRes]; unmapped keys resolve to `null` (no
 * icon), so this stays a no-op until a screen actually sets an `iconKey`.
 */
@Composable
fun ProvideAppLocals(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStrings provides AndroidStrings(LocalContext.current),
        LocalCategoryIcon provides { category -> painterResource(categoryDrawableRes(category)) },
        LocalSettingsIcon provides { key -> settingsDrawableRes(key)?.let { painterResource(it) } },
        content = content,
    )
}
