package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

private class FakeAppSettingsService(initial: AppSettingsSnapshot) : AppSettingsService {
    val _snap = MutableStateFlow(initial)
    override val snapshot: StateFlow<AppSettingsSnapshot> get() = _snap
    val boolWrites = mutableListOf<Pair<String, Boolean>>()
    val setWrites = mutableListOf<Pair<String, Set<String>>>()
    var lastInt: Pair<String, Int>? = null
    override fun setBool(key: String, value: Boolean) { boolWrites += key to value }
    override fun setString(key: String, value: String) {}
    override fun setInt(key: String, value: Int) { lastInt = key to value }
    override fun setStringSet(key: String, value: Set<String>) { setWrites += key to value }
    override fun refresh() {}
}

class AppSettingsControllerTest {
    private fun snap(
        hasAnyDictionary: Boolean = true,
        persecutionVisible: Boolean = true,
        calculatorPinVisible: Boolean = true,
        betaFeaturesVisible: Boolean = false,
        sdcardPermissionVisible: Boolean = false,
        openLinksVisible: Boolean = false,
    ) = AppSettingsSnapshot(
        greekDicts = emptySet(), hebrewDicts = emptySet(), greekMorph = emptySet(),
        enabledWordLookupDicts = emptySet(),
        greekDictOptions = emptyList(), hebrewDictOptions = emptyList(),
        greekMorphOptions = emptyList(), wordLookupDictOptions = emptyList(),
        hasAnyDictionary = hasAnyDictionary,
        navigateToVerse = false, openLinksInSpecialWindow = true, screenKeepOn = false,
        doubleTapToFullscreen = true, autoFullscreen = false, toolbarButtonActions = "",
        bibleViewSwipeMode = "CHAPTER", disableTwoStepBookmarking = false, volumeKeysScroll = true,
        nightMode = "manual", locale = "", disableClickToEdit = false, notesContentType = "HTML",
        fontSizeMultiplier = 100, hideStatusBar = false, fullScreenHideButtons = true,
        hideWindowButtons = false, hideBibleReferenceOverlay = false, showActiveWindowIndicator = true,
        enabledBibleBookmarkModalButtons = emptySet(), enabledGenBookmarkModalButtons = emptySet(),
        bibleBookmarkModalOptions = emptyList(), genBookmarkModalOptions = emptyList(),
        displayColorMode = "normal", einkMode = false, disableAnimations = false,
        discreteMode = false, showCalculator = false, calculatorPin = "1234",
        experimentalFeatures = emptySet(), experimentalFeatureOptions = emptyList(),
        enableBluetooth = true, requestSdcardPermission = false, showErrorbox = false, useComposeUi = true,
        toolbarButtonActionChoices = emptyList(), bibleViewSwipeModeChoices = emptyList(),
        nightModeChoices = emptyList(), localeChoices = emptyList(), notesContentTypeChoices = emptyList(),
        displayColorModeChoices = emptyList(),
        persecutionVisible = persecutionVisible, calculatorPinVisible = calculatorPinVisible,
        betaFeaturesVisible = betaFeaturesVisible, sdcardPermissionVisible = sdcardPermissionVisible,
        openLinksVisible = openLinksVisible,
    )

    private fun controller(s: AppSettingsService) =
        AppSettingsController(s, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            AppSettingsLabels.forTest(), onNavigate = {})

    @Test fun dictionariesCategoryHiddenWhenNoBooks() {
        val c = controller(FakeAppSettingsService(snap(hasAnyDictionary = false)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertFalse(keys.contains("dictionaries_category"))
        assertFalse(keys.contains("strongs_greek_dictionary"))
    }

    @Test fun persecutionRowsHiddenWhenNotAvailable() {
        val c = controller(FakeAppSettingsService(snap(persecutionVisible = false)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertFalse(keys.contains("discrete_mode"))
        assertFalse(keys.contains("calculator_pin"))
    }

    @Test fun calculatorPinHiddenUnlessShowCalculatorOn() {
        val c = controller(FakeAppSettingsService(snap(calculatorPinVisible = false)))
        assertFalse(c.state.value.visibleItems.map { it.key }.contains("calculator_pin"))
        assertTrue(c.state.value.visibleItems.map { it.key }.contains("discrete_mode"))
    }

    @Test fun betaAndSdcardAndOpenLinksGating() {
        val c = controller(FakeAppSettingsService(snap()))  // all beta/sdcard/openlinks false
        val keys = c.state.value.visibleItems.map { it.key }
        assertFalse(keys.contains("crash_app"))
        assertFalse(keys.contains("show_errorbox"))
        assertFalse(keys.contains("request_sdcard_permission_pref"))
        assertFalse(keys.contains("open_links"))
    }

    @Test fun switchWriteGoesThroughByKey() {
        val svc = FakeAppSettingsService(snap())
        controller(svc).onSwitch("eink_mode", true)
        assertEquals("eink_mode" to true, svc.boolWrites.single())
    }

    @Test fun multiSelectWriteForwardsSetByKey() {
        val svc = FakeAppSettingsService(snap())
        controller(svc).onMultiSelectChange("experimental_features", setOf("x"))
        assertEquals("experimental_features" to setOf("x"), svc.setWrites.single())
    }

    @Test fun fontSliderWritesInt() {
        val svc = FakeAppSettingsService(snap())
        controller(svc).onSliderChange("font_size_multiplier", 220)
        assertEquals("font_size_multiplier" to 220, svc.lastInt)
    }

    @Test fun developerAndUseComposeAlwaysPresent() {
        val c = controller(FakeAppSettingsService(snap()))
        assertTrue(c.state.value.visibleItems.map { it.key }.contains("use_compose_ui"))
    }
}
