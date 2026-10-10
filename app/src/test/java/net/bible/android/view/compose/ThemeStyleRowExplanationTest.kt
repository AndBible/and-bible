package net.bible.android.view.compose

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsLabels
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.settings.AppSettingsSnapshot
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DictOption
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.settings.AbSettingsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ThemeStyleRowExplanationTest {
    @get:Rule val compose = createComposeRule()

    private fun dictOptions(vararg pairs: Pair<String, String>) = pairs.map { DictOption(it.first, it.second) }
    private fun choices(vararg pairs: Pair<String, String>) = pairs.map { Choice2(it.first, it.second) }

    private fun baseSnapshot() = AppSettingsSnapshot(
        greekDicts = setOf("StrongsRealGreek"),
        hebrewDicts = setOf("StrongsRealHebrew"),
        greekMorph = setOf("Robinson"),
        enabledWordLookupDicts = setOf("StrongsRealGreek", "StrongsRealHebrew"),
        greekDictOptions = dictOptions("StrongsRealGreek" to "Strong's Greek Dictionary"),
        hebrewDictOptions = dictOptions("StrongsRealHebrew" to "Strong's Hebrew Dictionary"),
        greekMorphOptions = dictOptions("Robinson" to "Robinson's Morphological Analysis Codes"),
        wordLookupDictOptions = dictOptions(
            "StrongsRealGreek" to "Strong's Greek Dictionary",
            "StrongsRealHebrew" to "Strong's Hebrew Dictionary",
        ),
        hasAnyDictionary = true,
        navigateToVerse = true,
        openLinksInSpecialWindow = false,
        screenKeepOn = false,
        doubleTapToFullscreen = true,
        autoFullscreen = false,
        toolbarButtonActions = "bookmark",
        bibleViewSwipeMode = "verse_change",
        disableTwoStepBookmarking = false,
        volumeKeysScroll = true,
        nightMode = "manual",
        themePaletteStyle = "fidelity",
        locale = "",
        disableClickToEdit = false,
        notesContentType = "plain_text",
        fontSizeMultiplier = 150,
        hideStatusBar = false,
        fullScreenHideButtons = false,
        hideWindowButtons = false,
        hideBibleReferenceOverlay = false,
        showActiveWindowIndicator = true,
        enabledBibleBookmarkModalButtons = setOf("assign_labels", "compare"),
        enabledGenBookmarkModalButtons = setOf("assign_labels"),
        bibleBookmarkModalOptions = dictOptions("assign_labels" to "Assign labels", "compare" to "Compare"),
        genBookmarkModalOptions = dictOptions("assign_labels" to "Assign labels"),
        displayColorMode = "normal",
        einkMode = false,
        disableAnimations = false,
        discreteMode = false,
        showCalculator = false,
        calculatorPin = "",
        experimentalFeatures = setOf("new_search"),
        experimentalFeatureOptions = dictOptions("new_search" to "New search UI"),
        enableBluetooth = false,
        requestSdcardPermission = false,
        showErrorbox = false,
        toolbarButtonActionChoices = choices("bookmark" to "Bookmark", "share" to "Share", "speak" to "Speak"),
        bibleViewSwipeModeChoices = choices("verse_change" to "Change verse", "day_change" to "Change day"),
        nightModeChoices = choices("manual" to "Manual", "automatic" to "Follow system"),
        themePaletteStyleChoices = choices("fidelity" to "Fidelity (exact color)", "tonal_spot" to "Tonal spot (soft)"),
        localeChoices = choices("" to "System default", "en" to "English", "fi" to "Finnish"),
        notesContentTypeChoices = choices("plain_text" to "Plain text", "markdown" to "Markdown"),
        displayColorModeChoices = choices("normal" to "Normal", "eink" to "E-ink"),
        discreteTogglesVisible = false,
        betaFeaturesVisible = true,
        sdcardPermissionVisible = true,
        openLinksVisible = true,
    )

    private fun show(mode: DisplayColorMode) {
        val job = Job()
        val service = object : AppSettingsService {
            override val snapshot = MutableStateFlow(baseSnapshot().copy(displayColorMode = mode.value))
            override fun setBool(key: String, value: Boolean) = Unit
            override fun setString(key: String, value: String) = Unit
            override fun setInt(key: String, value: Int) = Unit
            override fun setStringSet(key: String, value: Set<String>) = Unit
            override fun refresh() = Unit
        }
        val controller = AppSettingsController(service, CoroutineScope(job), AppSettingsLabels.forTest(), {})
        val row = controller.state.value.items.filterIsInstance<SettingsItem.ListChoiceRow>()
            .single { it.key == "theme_palette_style" }
        job.cancel()
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = mode, disableAnimations = true) {
                    AbSettingsScreen(
                        state = SettingsScreenState(title = "Settings", items = listOf(row)),
                        onUp = null, onSwitch = { _, _ -> }, onListChoice = { _, _ -> },
                        onTextInput = { _, _ -> }, onNavigate = {},
                    )
                }
            }
        }
    }

    @Test fun blackAndWhiteShowsDisabledExplanationForValidFidelitySelection() {
        show(DisplayColorMode.BW)
        compose.onNodeWithText("Theme style").assertIsNotEnabled()
        compose.onNodeWithText("Applies only when Color mode is Normal.", substring = true).assertExists()
    }

    @Test fun colorEinkShowsDisabledExplanationForValidFidelitySelection() {
        show(DisplayColorMode.COLOR_EINK)
        compose.onNodeWithText("Theme style").assertIsNotEnabled()
        compose.onNodeWithText("Applies only when Color mode is Normal.", substring = true).assertExists()
    }

    @Test fun normalModeKeepsSelectedLabelInsteadOfExplanation() {
        show(DisplayColorMode.NORMAL)
        compose.onNodeWithText("Fidelity (exact color)").assertExists()
        compose.onNodeWithText("Applies only when Color mode is Normal.", substring = true).assertDoesNotExist()
    }
}
