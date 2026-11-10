package net.bible.sharedui.components

import androidx.compose.ui.text.font.FontFamily
import net.bible.sharedcore.settings.SettingsItem

/** A missing or invalid preview inherits the normal row typography, without breaking the chooser. */
internal fun choiceFontFamily(font: SettingsItem.ChoiceFont?): FontFamily? = when (font) {
    null -> null
    is SettingsItem.ChoiceFont.Family -> when (font.name) {
        "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> runCatching { platformChoiceFont(font) }.getOrNull()
    }
    is SettingsItem.ChoiceFont.File -> runCatching { platformChoiceFont(font) }.getOrNull()
}

/** Must load eagerly: lazy font errors would escape the chooser's fallback boundary during layout. */
internal expect fun platformChoiceFont(font: SettingsItem.ChoiceFont): FontFamily?
