package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.reading.LocalPinIcon
import net.bible.sharedui.settings.LocalSettingsIcon
import net.bible.sharedui.strings.AndroidStrings
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalSystemBarSync

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
 *
 * [LocalPinIcon] supplies classic's `ic_pin` to [net.bible.sharedui.reading.WindowButton]'s
 * Pane-mode pin indicator.
 *
 * [LocalSystemBarSync] resolves the current [LocalContext]'s Activity (via [findActivity]) and calls
 * [applySystemBarColor] on it; it no-ops only when there is no Activity at all (e.g. an isolated
 * `@Preview`). The Roborazzi golden harness is NOT that case — `captureRoboImage` runs inside a real
 * `ComponentActivity`, so this provider's real implementation fires there too. `GoldenHarness`
 * overrides [LocalSystemBarSync] back to a no-op of its own, after this provider, so golden captures
 * stay pixel-inert by construction rather than by coincidence.
 */
@Composable
fun ProvideAppLocals(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(
        LocalStrings provides AndroidStrings(context),
        LocalCategoryIcon provides { category -> painterResource(categoryDrawableRes(category)) },
        LocalPinIcon provides { painterResource(R.drawable.ic_pin) },
        LocalSettingsIcon provides { key -> settingsDrawableRes(key)?.let { painterResource(it) } },
        // A/B batch 3 F1. Resolves the Activity lazily on each call and no-ops only when there is
        // none (e.g. an isolated `@Preview`, or a plain-`Context` caller). The Roborazzi golden
        // harness is NOT that case — `captureRoboImage` runs inside a real `ComponentActivity`, so
        // this real implementation fires there too; the harness's pixel-inertness instead comes from
        // `GoldenHarness`'s own explicit `LocalSystemBarSync` no-op override (see its kdoc).
        LocalSystemBarSync provides { container: Color, fillWindowBackground: Boolean ->
            context.findActivity()?.let { applySystemBarColor(it, container, fillWindowBackground) }
        },
        content = content,
    )
}
