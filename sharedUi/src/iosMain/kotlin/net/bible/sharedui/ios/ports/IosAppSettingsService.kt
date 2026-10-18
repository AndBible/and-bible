/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.settings.AppSettingsSnapshot
import net.bible.sharedui.ios.IosPending

/** Pending on iOS (stream H): the snapshot is the [AppSettings] defaults (Android key defaults); setters only log. */
@kotlin.native.HiddenFromObjC
class IosAppSettingsService : AppSettingsService {
    private val state = MutableStateFlow(defaults())
    override val snapshot: StateFlow<AppSettingsSnapshot> get() = state

    override fun setBool(key: String, value: Boolean) = IosPending.log("AppSettingsService.setBool")
    override fun setString(key: String, value: String) = IosPending.log("AppSettingsService.setString")
    override fun setInt(key: String, value: Int) = IosPending.log("AppSettingsService.setInt")
    override fun setStringSet(key: String, value: Set<String>) = IosPending.log("AppSettingsService.setStringSet")
    override fun refresh() = IosPending.log("AppSettingsService.refresh")

    internal companion object {
        fun defaults() = AppSettingsSnapshot(
            greekDicts = emptySet(), hebrewDicts = emptySet(), greekMorph = emptySet(), enabledWordLookupDicts = emptySet(),
            greekDictOptions = emptyList(), hebrewDictOptions = emptyList(), greekMorphOptions = emptyList(),
            wordLookupDictOptions = emptyList(), hasAnyDictionary = false,
            navigateToVerse = false, openLinksInSpecialWindow = true, screenKeepOn = false, doubleTapToFullscreen = true,
            autoFullscreen = false, toolbarButtonActions = "default", bibleViewSwipeMode = "CHAPTER",
            disableTwoStepBookmarking = false, volumeKeysScroll = true, nightMode = "system",
            themePaletteStyle = ThemePaletteStyle.DEFAULT.value,
            locale = "", disableClickToEdit = false, notesContentType = "HTML", fontSizeMultiplier = 100,
            hideStatusBar = false, fullScreenHideButtons = false, hideWindowButtons = false,
            hideBibleReferenceOverlay = false, showActiveWindowIndicator = true,
            enabledBibleBookmarkModalButtons = emptySet(), enabledGenBookmarkModalButtons = emptySet(),
            bibleBookmarkModalOptions = emptyList(), genBookmarkModalOptions = emptyList(),
            displayColorMode = DisplayColorMode.defaultFor(false).value, einkMode = false, disableAnimations = false,
            discreteMode = false, showCalculator = false, calculatorPin = "1234",
            experimentalFeatures = emptySet(), experimentalFeatureOptions = emptyList(),
            enableBluetooth = true, requestSdcardPermission = false, showErrorbox = false,
            toolbarButtonActionChoices = emptyList(), bibleViewSwipeModeChoices = emptyList(), nightModeChoices = emptyList(),
            themePaletteStyleChoices = emptyList(), localeChoices = emptyList(), notesContentTypeChoices = emptyList(),
            displayColorModeChoices = emptyList(),
            discreteTogglesVisible = false, betaFeaturesVisible = false, sdcardPermissionVisible = false,
            openLinksVisible = false, debugToolsVisible = false,
        )
    }
}
