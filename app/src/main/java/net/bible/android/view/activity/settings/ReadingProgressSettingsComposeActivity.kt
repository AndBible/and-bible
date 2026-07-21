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
package net.bible.android.view.activity.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.settings.ReadingProgressSettingsController
import net.bible.sharedcore.settings.ReadingProgressSettingsLabels
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.settings.AbSettingsScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the reading-progress/memorization settings screen — the new-path twin of
 * classic [net.bible.android.view.activity.progress.ReadingProgressSettingsActivity]
 * (`R.xml.reading_progress_settings`). This is the simplest of the Batch 10c hosts: a flat list
 * of switches + one list-choice row, no navigation rows, no reset action, no recreate parity to
 * worry about. Modelled on [net.bible.android.view.activity.speak.SpeakSettingsComposeActivity].
 */
class ReadingProgressSettingsComposeActivity : ActivityBase() {
    private val service: ReadingProgressSettingsService by inject()

    private val labels by lazy { buildLabels() }

    private val controller by lazy {
        ReadingProgressSettingsController(
            service = service,
            scope = lifecycleScope,
            labels = labels,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val state by controller.state.collectAsState()
                    AbSettingsScreen(
                        state = state,
                        onUp = { finish() },
                        onSwitch = controller::onSwitch,
                        onListChoice = controller::onListChoice,
                        onTextInput = { _, _ -> },
                        onNavigate = { _ -> },
                    )
                }
            }
        }
    }

    private fun buildLabels() = ReadingProgressSettingsLabels(
        screenTitle = getString(R.string.reading_progress_settings),
        autoMarkMemorizedTitle = getString(R.string.memorize_auto_mark),
        autoMarkMemorizedSummary = getString(R.string.memorize_auto_mark_summary),
        memorizeTypeFullWordsTitle = getString(R.string.memorize_type_full_words),
        memorizeTypeFullWordsSummary = getString(R.string.memorize_type_full_words_summary),
        memorizeWordVisibilityTitle = getString(R.string.memorize_word_visibility),
        memorizeWordVisibilitySummary = getString(R.string.memorize_word_visibility_summary),
        memorizeErrorHeatmapTitle = getString(R.string.memorize_error_heatmap),
        memorizeErrorHeatmapSummary = getString(R.string.memorize_error_heatmap_summary),
        memorizeScrambleHideUsedTitle = getString(R.string.memorize_scramble_hide_used),
        memorizeScrambleHideUsedSummary = getString(R.string.memorize_scramble_hide_used_summary),
        memorizeIncludeReferenceTitle = getString(R.string.memorize_include_reference),
        memorizeIncludeReferenceSummary = getString(R.string.memorize_include_reference_summary),
    )
}
