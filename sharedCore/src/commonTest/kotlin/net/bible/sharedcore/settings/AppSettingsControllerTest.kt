package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        discreteTogglesVisible: Boolean = true,
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
        discreteTogglesVisible = discreteTogglesVisible,
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

    @Test fun persecutionCategoryHelpAndPinAlwaysVisible_evenWhenDiscreteTogglesHidden() {
        // Classic parity: a discrete build hides ONLY discrete_mode + show_calculator; the category
        // header, discrete_help and calculator_pin (the PIN setter) stay reachable.
        val c = controller(FakeAppSettingsService(snap(discreteTogglesVisible = false)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertTrue(keys.contains("prefs_persecution_cat"))
        assertTrue(keys.contains("discrete_help"))
        assertTrue(keys.contains("calculator_pin"))
        assertFalse(keys.contains("discrete_mode"))
        assertFalse(keys.contains("show_calculator"))
    }

    @Test fun discreteTogglesShownWhenVisible() {
        val c = controller(FakeAppSettingsService(snap(discreteTogglesVisible = true)))
        val keys = c.state.value.visibleItems.map { it.key }
        assertTrue(keys.contains("discrete_mode"))
        assertTrue(keys.contains("show_calculator"))
        assertTrue(keys.contains("calculator_pin"))
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

    @Test fun fontSliderValueLabelUnescapesPercent() {
        val c = controller(FakeAppSettingsService(snap()))
        val row = c.state.value.visibleItems.single { it.key == "font_size_multiplier" } as SettingsItem.SliderRow
        assertEquals("100 %", row.valueLabel)
    }

    /**
     * Round 14b: the 41 rows classic gives an `android:icon` in `res/xml/settings.xml` take their own
     * key as `iconKey`; the two rows classic leaves bare stay bare, as do all category headers. The
     * key→drawable half of the parity claim is pinned separately by `SettingsIconParityTest` in `:app`.
     */
    @Test fun iconKeysMatchClassicSettingsXml() {
        val iconless = setOf("request_sdcard_permission_pref", "use_compose_ui")
        val items = controller(
            FakeAppSettingsService(
                snap(betaFeaturesVisible = true, sdcardPermissionVisible = true, openLinksVisible = true),
            ),
        ).state.value.items
        val wrong = items.mapNotNull { item ->
            val expected = when {
                item is SettingsItem.Category -> null
                item.key in iconless -> null
                else -> item.key
            }
            if (item.iconKeyOrNull() == expected) null else "${item.key}: expected $expected, got ${item.iconKeyOrNull()}"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test fun fortyOneRowsCarryAnIconKey() {
        val items = controller(
            FakeAppSettingsService(
                snap(betaFeaturesVisible = true, sdcardPermissionVisible = true, openLinksVisible = true),
            ),
        ).state.value.items
        assertEquals(41, items.count { it.iconKeyOrNull() != null })
    }
}
