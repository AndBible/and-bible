package net.bible.android.view.activity.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.TEST_SDK
import net.bible.android.view.compose.golden.EDGE_MODE
import net.bible.android.view.compose.golden.captureGolden
import net.bible.android.view.compose.golden.captureMatrix
import net.bible.android.view.compose.golden.captureRtl
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsLabels
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.settings.AppSettingsSnapshot
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DictOption
import net.bible.sharedui.settings.AppSettingsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the main app [AppSettingsScreen] — the classic preference-screen port (Batch 10a).
 * Renders through the REAL [AppSettingsController] (a fake [AppSettingsService] feeds it a
 * hand-built [AppSettingsSnapshot]), rather than hand-duplicating the ~40-item list the controller
 * assembles, so the golden can't silently drift from `AppSettingsController.build()`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppSettingsGoldenTest {

    /** Minimal fake: the golden only needs a fixed snapshot, so writes are no-ops. */
    private class FakeAppSettingsService(initial: AppSettingsSnapshot) : AppSettingsService {
        override val snapshot: StateFlow<AppSettingsSnapshot> = MutableStateFlow(initial)
        override fun setBool(key: String, value: Boolean) {}
        override fun setString(key: String, value: String) {}
        override fun setInt(key: String, value: Int) {}
        override fun setStringSet(key: String, value: Set<String>) {}
        override fun refresh() {}
    }

    private fun dictOptions(vararg pairs: Pair<String, String>) = pairs.map { DictOption(it.first, it.second) }

    private fun choices(vararg pairs: Pair<String, String>) = pairs.map { Choice2(it.first, it.second) }

    /** Representative baseline snapshot: dictionaries present, persecution section hidden. */
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
        useComposeUi = true,
        toolbarButtonActionChoices = choices("bookmark" to "Bookmark", "share" to "Share", "speak" to "Speak"),
        bibleViewSwipeModeChoices = choices("verse_change" to "Change verse", "day_change" to "Change day"),
        nightModeChoices = choices("manual" to "Manual", "automatic" to "Follow system"),
        localeChoices = choices("" to "System default", "en" to "English", "fi" to "Finnish"),
        notesContentTypeChoices = choices("plain_text" to "Plain text", "markdown" to "Markdown"),
        displayColorModeChoices = choices("normal" to "Normal", "eink" to "E-ink"),
        persecutionVisible = false,
        calculatorPinVisible = false,
        betaFeaturesVisible = true,
        sdcardPermissionVisible = true,
        openLinksVisible = true,
    )

    /** Edge state F1: no dictionary modules installed — the whole Dictionaries category disappears. */
    private fun emptyDictSnapshot() = baseSnapshot().copy(
        greekDicts = emptySet(),
        hebrewDicts = emptySet(),
        greekMorph = emptySet(),
        enabledWordLookupDicts = emptySet(),
        greekDictOptions = emptyList(),
        hebrewDictOptions = emptyList(),
        greekMorphOptions = emptyList(),
        wordLookupDictOptions = emptyList(),
        hasAnyDictionary = false,
    )

    /** Edge state F2: persecution-resistant settings visible AND the calculator PIN row shown too. */
    private fun persecutionSnapshot() = baseSnapshot().copy(
        persecutionVisible = true,
        discreteMode = true,
        showCalculator = true,
        calculatorPinVisible = true,
        calculatorPin = "1234",
    )

    /** Builds the real [AppSettingsController]'s state for [snapshot] (a fresh, uncollected scope is
     *  fine: the constructor computes `state.value` synchronously from `service.snapshot.value` before
     *  the background collector ever runs, and the fake service never emits again). */
    private fun stateFor(snapshot: AppSettingsSnapshot) =
        AppSettingsController(
            service = FakeAppSettingsService(snapshot),
            scope = CoroutineScope(Job()),
            labels = AppSettingsLabels.forTest(),
            onNavigate = {},
        ).state.value

    private fun screen(snapshot: AppSettingsSnapshot): @Composable () -> Unit = {
        AppSettingsScreen(
            state = stateFor(snapshot),
            onUp = {},
            onSwitch = { _, _ -> },
            onListChoice = { _, _ -> },
            onTextInput = { _, _ -> },
            onSliderChange = { _, _ -> },
            onMultiSelectChange = { _, _ -> },
            onNavigate = {},
            onReset = {},
            resetContentDescription = "Reset to defaults",
        )
    }

    // heightDp=3600: the full preference list (8 categories incl. persecution/beta rows) is much
    // longer than the default viewport; render the whole thing so nothing is clipped out of frame.
    @Test fun baseline_matrix() =
        captureMatrix("AppSettings", "baseline", heightDp = 3600, content = screen(baseSnapshot()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun baseline_rtl() =
        captureRtl("AppSettings", "baseline", heightDp = 3600, content = screen(baseSnapshot()))

    @Test fun emptydict_edge() =
        captureGolden("AppSettings", "emptydict", EDGE_MODE, heightDp = 3600, content = screen(emptyDictSnapshot()))

    @Test fun persecution_edge() =
        captureGolden("AppSettings", "persecution", EDGE_MODE, heightDp = 3600, content = screen(persecutionSnapshot()))
}
