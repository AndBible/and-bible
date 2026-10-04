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
        enableBluetooth = true, requestSdcardPermission = false, showErrorbox = false,
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

    @Test fun fontSliderValueLabelUnescapesPercent() {
        val c = controller(FakeAppSettingsService(snap()))
        val row = c.state.value.visibleItems.single { it.key == "font_size_multiplier" } as SettingsItem.SliderRow
        assertEquals("100 %", row.valueLabel)
    }

    /**
     * Round 14b: the 41 rows classic gives an `android:icon` in `res/xml/settings.xml` take their own
     * key as `iconKey`; the one row classic leaves bare stays bare, as do all category headers. The
     * key→drawable half of the parity claim is pinned separately by `SettingsIconParityTest` in `:app`.
     */
    @Test fun iconKeysMatchClassicSettingsXml() {
        val iconless = setOf("request_sdcard_permission_pref")
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

    /**
     * Round 14b fix wave: [iconKeysMatchClassicSettingsXml] only checks that `iconKey == item.key` —
     * it says nothing about what `key` itself should BE. Sync and Reading progress pin their full key
     * lists ([SyncSettingsControllerTest.theEighteenPortedSyncRowsAreAllPresent],
     * [ReadingProgressSettingsControllerTest.buildsSixRowsInOrder]), but this 41-row screen did not,
     * so a typo in a row's own `key` (e.g. `screen_keep_on_pref` misspelled) would propagate into its
     * `iconKey`, both the sharedCore test above and `:app`'s `SettingsIconParityTest` would stay
     * green, and the row would render with no icon on the device. Pinning the full ordered key list
     * (categories included) is what makes that mistake fail here instead of shipping silently.
     */
    @Test fun fullItemKeyOrderMatchesClassicSettingsScreen() {
        val expected = listOf(
            "dictionaries_category",
            "strongs_greek_dictionary",
            "strongs_hebrew_dictionary",
            "robinson_greek_morphology",
            "disabled_word_lookup_dictionaries",
            "behavior_category",
            "navigate_to_verse_pref",
            "open_links_in_special_window_pref",
            "screen_keep_on_pref",
            "double_tap_to_fullscreen",
            "auto_fullscreen_pref",
            "toolbar_button_actions",
            "bible_view_swipe_mode",
            "disable_two_step_bookmarking",
            "volume_keys_scroll",
            "night_mode_pref3",
            "display_category",
            "global_text_display_settings",
            "locale_pref",
            "disable_click_to_edit",
            "notes_content_type",
            "font_size_multiplier",
            "hide_status_bar",
            "full_screen_hide_buttons_pref",
            "hide_window_buttons",
            "hide_bible_reference_overlay",
            "show_active_window_indicator",
            "disable_bible_bookmark_modal_buttons",
            "disable_gen_bookmark_modal_buttons",
            "prefs_eink_settings_cat",
            "display_color_mode",
            "eink_mode",
            "disable_animations",
            "prefs_persecution_cat",
            "discrete_help",
            "discrete_mode",
            "show_calculator",
            "calculator_pin",
            "prefs_features_cat",
            "sync_settings_shortcut",
            "ai_settings_shortcut",
            "reading_progress_settings_shortcut",
            "prefs_advanced_settings_cat",
            "experimental_features",
            "enable_bluetooth_pref",
            "request_sdcard_permission_pref",
            "show_errorbox",
            "open_links",
            "crash_app",
        )
        val c = controller(
            FakeAppSettingsService(
                snap(betaFeaturesVisible = true, sdcardPermissionVisible = true, openLinksVisible = true),
            ),
        )
        assertEquals(expected, c.state.value.items.map { it.key })
    }

    // --- Task 15: reset confirm + discrete-mode help moved off the host into this controller's own
    // --- dialog state ------------------------------------------------------------------------------

    private fun controllerWithReset(onConfirmReset: () -> Unit) = AppSettingsController(
        FakeAppSettingsService(snap()),
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        AppSettingsLabels.forTest(), onNavigate = {}, onConfirmReset = onConfirmReset,
    )

    @Test fun dialog_startsNone() {
        val c = controller(FakeAppSettingsService(snap()))
        assertEquals(AppSettingsDialog.None, c.dialog.value)
    }

    @Test fun requestReset_showsConfirmWithTheLabelsMessage() {
        val c = controller(FakeAppSettingsService(snap()))
        c.requestReset()
        assertEquals(AppSettingsDialog.ConfirmReset(AppSettingsLabels.forTest().resetConfirmMessage), c.dialog.value)
    }

    @Test fun showDiscreteHelp_showsHelpWithThePersecutionCatTitle() {
        val c = controller(FakeAppSettingsService(snap()))
        c.showDiscreteHelp("<p>help</p>")
        assertEquals(AppSettingsDialog.DiscreteHelp(AppSettingsLabels.forTest().persecutionCat, "<p>help</p>"), c.dialog.value)
    }

    @Test fun confirmDialog_runsResetExactlyOnceAndClears() {
        var resetCount = 0
        val c = controllerWithReset { resetCount++ }
        c.requestReset()
        c.confirmDialog()
        assertEquals(1, resetCount)
        assertEquals(AppSettingsDialog.None, c.dialog.value)

        // Task 13 fix round 1's guard: a stray second call (e.g. a double-tap after the dialog
        // closed) must not run the reset again.
        c.confirmDialog()
        assertEquals(1, resetCount)
    }

    @Test fun confirmDialog_whenShowingDiscreteHelp_doesNotRunReset() {
        var resetCount = 0
        val c = controllerWithReset { resetCount++ }
        c.showDiscreteHelp("<p>help</p>")
        c.confirmDialog()
        assertEquals(0, resetCount)
        assertEquals(AppSettingsDialog.None, c.dialog.value)
    }

    @Test fun confirmDialog_whenNotShowing_isANoOp() {
        var resetCount = 0
        val c = controllerWithReset { resetCount++ }
        c.confirmDialog()
        assertEquals(0, resetCount)
    }

    @Test fun dismissDialog_runsNothingAndClears() {
        var resetCount = 0
        val c = controllerWithReset { resetCount++ }
        c.requestReset()
        c.dismissDialog()
        assertEquals(0, resetCount)
        assertEquals(AppSettingsDialog.None, c.dialog.value)
    }
}
