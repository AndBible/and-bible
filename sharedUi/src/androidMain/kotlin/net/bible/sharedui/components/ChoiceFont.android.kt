package net.bible.sharedui.components

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import net.bible.sharedcore.settings.SettingsItem
import java.io.File

internal actual fun platformChoiceFont(font: SettingsItem.ChoiceFont): FontFamily? = FontFamily(
    when (font) {
        is SettingsItem.ChoiceFont.File -> Typeface.createFromFile(File(font.path))
        is SettingsItem.ChoiceFont.Family -> Typeface.create(font.name, Typeface.NORMAL)
    }
)
