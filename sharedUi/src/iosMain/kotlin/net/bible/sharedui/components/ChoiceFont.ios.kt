package net.bible.sharedui.components

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import net.bible.sharedcore.settings.SettingsItem
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr

internal actual fun platformChoiceFont(font: SettingsItem.ChoiceFont): FontFamily? = when (font) {
    is SettingsItem.ChoiceFont.File -> {
        val bytes = FileSystem.SYSTEM.read(font.path.toPath()) { readByteArray() }
        val data = Data.makeFromBytes(bytes)
        try {
            FontMgr.default.makeFromData(data)?.let { FontFamily(Typeface(it)) }
        } finally {
            data.close()
        }
    }
    is SettingsItem.ChoiceFont.Family -> null // Portable families are handled by the common mapper.
}
