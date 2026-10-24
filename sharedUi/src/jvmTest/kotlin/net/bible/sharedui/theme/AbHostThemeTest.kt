package net.bible.sharedui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** In-memory [AppSettings]; only the typed getters used by the theme reads matter. */
private class MapSettings(private val map: MutableMap<String, Any?> = mutableMapOf()) : AppSettings {
    override fun getString(key: String, default: String?) = map[key] as String? ?: default
    override fun getLong(key: String, default: Long) = map[key] as Long? ?: default
    override fun getInt(key: String, default: Int) = map[key] as Int? ?: default
    override fun getBoolean(key: String, default: Boolean) = map[key] as Boolean? ?: default
    override fun getDouble(key: String, default: Double) = map[key] as Double? ?: default
    override fun getFloat(key: String, default: Float) = map[key] as Float? ?: default
    override fun setString(key: String, value: String?) { map[key] = value }
    override fun setLong(key: String, value: Long?) { map[key] = value }
    override fun setInt(key: String, value: Int?) { map[key] = value }
    override fun setBoolean(key: String, value: Boolean?) { map[key] = value }
    override fun setDouble(key: String, value: Double?) { map[key] = value }
    override fun setFloat(key: String, value: Float?) { map[key] = value }
}

private fun Color.saturation(): Float {
    val mx = maxOf(red, green, blue)
    val mn = minOf(red, green, blue)
    return if (mx == 0f) 0f else (mx - mn) / mx
}

@OptIn(ExperimentalTestApi::class)
class AbHostThemeTest {
    @Test
    fun darkSwitchRecomposesContentWithTheDarkScheme() = runComposeUiTest {
        val settings = MapSettings()
        var dark by mutableStateOf(false)
        var seen: Color? = null
        setContent {
            AbHostTheme(darkTheme = dark, settings = settings, isOnyx = false, workspaceSeed = { null }) {
                seen = MaterialTheme.colorScheme.background
            }
        }
        val light = seen
        dark = true
        waitForIdle()
        assertNotEquals(light, seen, "background must change when darkTheme flips")
        assertEquals(abColorScheme(null, true, net.bible.service.common.DisplayColorMode.NORMAL).background, seen)
    }

    @Test
    fun monochromeModeGivesGreyScheme() = runComposeUiTest {
        val settings = MapSettings().apply { setString("display_color_mode", "monochrome") }
        var primary: Color? = null
        setContent {
            AbHostTheme(darkTheme = false, settings = settings, isOnyx = false, workspaceSeed = { 0xFFE53935.toInt() }) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(0f, primary!!.saturation(), "monochrome primary must have zero saturation")
    }
}
