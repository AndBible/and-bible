package net.bible.sharedui.components

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import net.bible.sharedcore.settings.SettingsItem
import org.jetbrains.skia.FontMgr

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal actual fun platformChoiceFont(font: SettingsItem.ChoiceFont): FontFamily? = when (font) {
    // Compose's file Font silently substitutes a system font for corrupt bytes. Load the actual
    // face first so the common fallback inherits the app's typography rather than a Skia default.
    is SettingsItem.ChoiceFont.File -> FontMgr.default.makeFromFile(font.path)?.let {
        FontFamily(Typeface(it))
    }
    is SettingsItem.ChoiceFont.Family -> FontFamily(font.name)
}
