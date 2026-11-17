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
}
