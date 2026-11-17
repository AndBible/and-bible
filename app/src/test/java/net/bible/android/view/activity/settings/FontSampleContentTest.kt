package net.bible.android.view.activity.settings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.settings.NumericTextSettingContent
import net.bible.sharedui.strings.AndroidStrings
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontSampleContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fontSampleUsesAndroidStringsAndTracksTheSlider() {
        val strings = AndroidStrings(RuntimeEnvironment.getApplication())
        compose.setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    NumericTextSettingContent(
                        type = TextSettingType.FONTSIZE,
                        numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt"),
                        fontSize = 16, lineSpacing = 16,
                        font = SettingsItem.ChoiceFont.Family("serif"),
                        onValueChange = {},
                    )
                }
            }
        }
        fun style() = mutableListOf<TextLayoutResult>().also { layouts ->
            compose.onNodeWithText(strings.fontPreviewVerse, substring = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        }.single().layoutInput.style
        assertEquals(16.sp, style().fontSize)
        assertEquals(FontFamily.Serif, style().fontFamily)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(32f) }
        assertEquals(32.sp, style().fontSize)
        assertEquals(51.2f, style().lineHeight.value, 0.01f)
    }

    private fun sampleSizePx(multiplier: Int, fontScale: Float): Float {
        val strings = AndroidStrings(RuntimeEnvironment.getApplication())
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalDensity provides Density(base.density, fontScale),
            ) {
                AbTheme(darkTheme = false) {
                    NumericTextSettingContent(
                        type = TextSettingType.FONTSIZE,
                        numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt"),
                        fontSize = 16, lineSpacing = 16,
                        font = null,
                        onValueChange = {},
                        fontSizeMultiplierPercent = multiplier,
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(strings.fontPreviewVerse, substring = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        val density = layouts.single().layoutInput.density
        // Rendered size in px: sp -> px through the density that laid the text out.
        return with(density) { style.fontSize.toPx() }
    }

    private val screenDensity get() = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    /** BibleView-JS renders fontSize * multiplier CSS px (= dp): 16 * 150% = 24 dp. */
    @Test fun multiplierScalesTheSampleLikeTheBibleView() {
        assertEquals(24f * screenDensity, sampleSizePx(150, 1f), 0.01f)
    }

    @Test fun systemFontScaleDoesNotChangeTheSampleSize() {
        val px = sampleSizePx(100, 2f)
        // 16 CSS px = 16 dp, whatever the system font scale: 16 * density px.
        assertEquals(16f * screenDensity, px, 0.01f)
    }
}
