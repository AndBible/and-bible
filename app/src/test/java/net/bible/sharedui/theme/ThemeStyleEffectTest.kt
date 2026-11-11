package net.bible.sharedui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.nhaarman.mockitokotlin2.mock
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A restored legacy preference must never leave an invisible, unchangeable palette active. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ThemeStyleEffectTest {
    @get:Rule val compose = createComposeRule()

    private data class Case(val seed: Int?, val dark: Boolean, val legacyStyle: String?)

    @Test fun legacyStoredStylesAreIgnoredByTheRealHostInDayAndNight() {
        val cases = listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB, 0xFF9C27B0.toInt()).flatMap { seed ->
            listOf(false, true).flatMap { dark ->
                listOf(null, "tonal_spot_grey", "fidelity", "tonal_spot", "content", "neutral",
                    "monochrome", "vibrant", "expressive", "rainbow", "fruit_salad", "unknown")
                    .map { Case(seed, dark, it) }
            }
        }
        val observed = mutableMapOf<Case, Int>()
        compose.setContent {
            cases.forEach { case ->
                // Substitute only storage; exercise production preference resolution and theme composition.
                val settings = object : AppSettings by mock<AppSettings>() {
                    override fun getString(key: String, default: String?): String? = when (key) {
                        "display_color_mode" -> DisplayColorMode.NORMAL.value
                        "theme_palette_style" -> case.legacyStyle ?: default
                        else -> default
                    }
                    override fun getBoolean(key: String, default: Boolean): Boolean = default
                }
                AbHostTheme(case.dark, settings, false, { case.seed }) {
                    val toolbar = MaterialTheme.colorScheme.primaryContainer.toArgb()
                    SideEffect { observed[case] = toolbar }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(cases.size, observed.size)
        observed.forEach { (case, toolbar) ->
            // Independently captured baseline roles from the pre-removal default palette.
            val expected = when {
                case.seed == 0xFF9C27B0.toInt() -> if (case.dark) 0xFF613766 else 0xFFFFD6FE
                else -> if (case.dark) 0xFF4F378B else 0xFFEADDFF
            }.toInt()
            assertEquals("Fixed default for $case", expected, toolbar)
        }
    }
}
