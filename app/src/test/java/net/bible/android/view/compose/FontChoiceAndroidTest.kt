package net.bible.android.view.compose

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbListChoiceContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontChoiceAndroidTest {
    @get:Rule val compose = createComposeRule()

    private fun rowFont(path: String): FontFamily? {
        compose.setContent {
            MaterialTheme(typography = Typography(bodyLarge = TextStyle(fontFamily = FontFamily.Cursive))) {
                AbListChoiceContent(
                    listOf(SettingsItem.Choice("file", "Font preview", SettingsItem.ChoiceFont.File(path))),
                    "file", {},
                )
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Font preview", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().layoutInput.style.fontFamily
    }

    @Test fun unreadableFontKeepsTheNormalRowFont() {
        val file = File.createTempFile("broken-preview", ".ttf")
        try {
            file.writeText("not a font")
            assertEquals(FontFamily.Cursive, rowFont(file.absolutePath))
        } finally { file.delete() }
    }

    @Test
    @Config(sdk = [23])
    fun unreadableFontOnApi23KeepsTheNormalRowFont() = unreadableFontKeepsTheNormalRowFont()

    @Test
    @Config(sdk = [23])
    fun installedFontOnApi23UsesItsNativeTypeface() = installedFontUsesItsNativeTypeface()

    @Test fun installedFontUsesItsNativeTypeface() {
        val file = File("../iosApp/test-fixtures/fonts/StudyMono.ttf")
        org.junit.Assert.assertTrue("real font fixture must exist", file.isFile)
        assertNotEquals(FontFamily.Cursive, rowFont(file.absolutePath))
    }
}
