package net.bible.sharedui.components

import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import net.bible.sharedcore.settings.SettingsItem
import java.io.File

internal actual fun platformChoiceFont(font: SettingsItem.ChoiceFont): FontFamily? {
    val typeface = when (font) {
        is SettingsItem.ChoiceFont.File -> {
            val file = File(font.path)
            // createFromFile substitutes DEFAULT for corrupt files. Builder instead returns null.
            if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(file).build()
            else {
                // Older Android loaders may also silently substitute a face. Reject non-SFNT
                // files before loading; TTF, OpenType and font collections are supported.
                val signature = file.inputStream().use { java.io.DataInputStream(it).readInt() }
                if (signature !in listOf(0x00010000, 0x4F54544F, 0x74746366, 0x74727565)) null
                else Typeface.createFromFile(file).takeUnless { it == Typeface.DEFAULT }
            }
        }
        is SettingsItem.ChoiceFont.Family -> Typeface.create(font.name, Typeface.NORMAL)
    }
    return typeface?.let { FontFamily(it) }
}
