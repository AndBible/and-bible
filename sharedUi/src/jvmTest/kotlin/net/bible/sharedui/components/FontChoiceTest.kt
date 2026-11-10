package net.bible.sharedui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.service.common.DisplayColorMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import net.bible.sharedcore.settings.SettingsItem
import java.nio.file.Files
import net.bible.sharedui.strings.keyed.genericFontChoice

@OptIn(ExperimentalTestApi::class)
class FontChoiceTest {
    @Test fun genericFamiliesAndOrdinaryChoicesKeepTheirOwnTypography() {
        assertEquals(FontFamily.SansSerif, choiceFontFamily(genericFontChoice("sans-serif").font))
        assertEquals(FontFamily.Serif, choiceFontFamily(genericFontChoice("serif").font))
        assertEquals(FontFamily.Monospace, choiceFontFamily(genericFontChoice("monospace").font))
        assertNull(choiceFontFamily(SettingsItem.Choice("serif", "Not a font setting").font))
    }

    @Test fun missingAndCorruptFilesFallBackBeforeLayout() {
        val file = Files.createTempFile("invalid-font", ".ttf")
        try {
            Files.writeString(file, "not a font")
            assertNull(choiceFontFamily(SettingsItem.ChoiceFont.File(file.toString())))
            Files.delete(file)
            assertNull(choiceFontFamily(SettingsItem.ChoiceFont.File(file.toString())))
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun installedFontFileLoadsForPreview() = runComposeUiTest {
        val file = java.io.File("../iosApp/test-fixtures/fonts/StudyMono.ttf")
        kotlin.test.assertTrue(file.isFile, "real font fixture must exist")
        val font = SettingsItem.ChoiceFont.File(file.absolutePath)
        assertNotNull(choiceFontFamily(font))
        setContent {
            MaterialTheme {
                AbListChoiceContent(listOf(SettingsItem.Choice("study", "Study font", font)), "study", {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Study font", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        kotlin.test.assertNotEquals(FontFamily.SansSerif, layouts.single().layoutInput.style.fontFamily)
    }

    @Test fun unloadableRowInheritsTheAmbientTypeface() = runComposeUiTest {
        val file = Files.createTempFile("broken-preview", ".ttf")
        try {
            Files.writeString(file, "not a font")
            setContent {
                MaterialTheme(typography = androidx.compose.material3.Typography(
                    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Cursive),
                )) {
                    AbListChoiceContent(
                        listOf(SettingsItem.Choice("broken", "Broken font", SettingsItem.ChoiceFont.File(file.toString()))),
                        "broken", {},
                    )
                }
            }
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText("Broken font", useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertEquals(FontFamily.Cursive, layouts.single().layoutInput.style.fontFamily)
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun sheetKeepsPreviewAndSelectionAcrossDisplayModes() {
        for (dark in listOf(false, true)) {
            for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.MONOCHROME)) {
                runComposeUiTest {
                    val selected = mutableStateOf("sans-serif")
                    setContent {
                        AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                                AbChoiceSheetContent(
                                    title = "Fonts",
                                    choices = listOf(genericFontChoice("sans-serif"), genericFontChoice("serif")),
                                    selectedValue = selected.value,
                                    onSelect = { selected.value = it },
                                    onClose = {},
                                )
                            }
                        }
                    }
                    onNodeWithText("Serif").performClick()
                    assertEquals("serif", selected.value)
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("Serif", useUnmergedTree = true).fetchSemanticsNode()
                        .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                    assertEquals(FontFamily.Serif, layouts.single().layoutInput.style.fontFamily)
                    if (mode == DisplayColorMode.MONOCHROME) {
                        val color = layouts.single().layoutInput.style.color
                        assertEquals(color.red, color.green)
                        assertEquals(color.green, color.blue)
                    }
                }
            }
        }
    }

    @Test fun fontChoiceRowUsesTheSelectedTypeface() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AbListChoiceContent(listOf(genericFontChoice("serif")), "serif", {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Serif", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(FontFamily.Serif, layouts.single().layoutInput.style.fontFamily)
    }
}
