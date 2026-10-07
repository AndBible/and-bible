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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.common.ReadingProgressSettings
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.sharedcore.settings.ReadingProgressSettingsSnapshot

/**
 * Android impl of [ReadingProgressSettingsService]. Reproduces the classic
 * `ReadingProgressSettingsDataStore` (its file was deleted in the "Z-late S12: delete the classic
 * settings cluster" commit; see that commit and its history for the source of truth this was
 * written against) key-for-key: reads/writes go through the [ReadingProgressSettings] singleton
 * (Room-DAO-backed, `net.bible.service.common.ReadingProgressSettings`), NOT
 * `CommonUtils.settings`. That singleton's property setters do NOT themselves post
 * [ReadingProgressSettings.notifyChanged] (they only write to the DAO) — the classic
 * `PreferenceDataStore.putBoolean`/`putString` post the event explicitly after every
 * write, so this impl does the same, to preserve parity (BibleView listens for this
 * event to refresh its reading-progress markers).
 */
class ReadingProgressSettingsServiceImpl : ReadingProgressSettingsService {

    private val _snapshot = MutableStateFlow(build())
    override val snapshot: StateFlow<ReadingProgressSettingsSnapshot> = _snapshot.asStateFlow()

    private fun memorizeWordVisibilityChoices(): List<Choice2> {
        val labels = application.resources.getStringArray(R.array.memorize_word_visibility_entries)
        val values = application.resources.getStringArray(R.array.memorize_word_visibility_values)
        return values.indices.map { Choice2(values[it], labels.getOrElse(it) { values[it] }) }
    }

    private fun build(): ReadingProgressSettingsSnapshot {
        val bundle = ReadingProgressSettings.getBundle()
        return ReadingProgressSettingsSnapshot(
            autoMarkMemorized = bundle.autoMarkMemorized,
            memorizeTypeFullWords = bundle.memorizeTypeFullWords,
            memorizeWordVisibility = bundle.memorizeWordVisibility,
            memorizeWordVisibilityChoices = memorizeWordVisibilityChoices(),
            memorizeErrorHeatmap = bundle.memorizeErrorHeatmap,
            memorizeScrambleHideUsed = bundle.memorizeScrambleHideUsed,
            memorizeIncludeReference = bundle.memorizeIncludeReference,
        )
    }

    override fun setBool(key: String, value: Boolean) {
        when (key) {
            "auto_mark_memorized" -> ReadingProgressSettings.autoMarkMemorized = value
            "memorize_type_full_words" -> ReadingProgressSettings.memorizeTypeFullWords = value
            "memorize_error_heatmap" -> ReadingProgressSettings.memorizeErrorHeatmap = value
            "memorize_scramble_hide_used" -> ReadingProgressSettings.memorizeScrambleHideUsed = value
            "memorize_include_reference" -> ReadingProgressSettings.memorizeIncludeReference = value
        }
        ReadingProgressSettings.notifyChanged()
        refresh()
    }

    override fun setString(key: String, value: String) {
        when (key) {
            "memorize_word_visibility" -> ReadingProgressSettings.memorizeWordVisibility = value
        }
        ReadingProgressSettings.notifyChanged()
        refresh()
    }

    override fun refresh() {
        _snapshot.value = build()
    }
}
