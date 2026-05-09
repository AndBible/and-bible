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

package net.bible.service.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.service.db.DatabaseContainer

/**
 * Serializable bundle of all reading progress settings, used for communication with Vue.js.
 */
@Serializable
data class ReadingProgressSettingsBundle(
    val autoMarkMemorized: Boolean = true,
    val memorizeTypeFullWords: Boolean = false,
    val memorizeWordVisibility: String = "light",
    val memorizeErrorHeatmap: Boolean = true,
    val memorizeScrambleHideUsed: Boolean = false,
    val memorizeIncludeReference: Boolean = true,
)

/**
 * Accessor for global reading progress settings stored in the syncable ProgressDatabase.
 * Each property reads/writes the [GlobalReadingProgressSettings] singleton row.
 */
object ReadingProgressSettings {
    private val dao get() = DatabaseContainer.instance.progressDb.globalReadingProgressSettingsDao()

    private fun getOrDefault(): GlobalReadingProgressSettings = dao.get() ?: GlobalReadingProgressSettings()

    private fun update(transform: GlobalReadingProgressSettings.() -> GlobalReadingProgressSettings) {
        dao.set(getOrDefault().transform())
    }

    var autoMarkMemorized: Boolean
        get() = getOrDefault().autoMarkMemorized
        set(value) = update { copy(autoMarkMemorized = value) }

    var memorizeTypeFullWords: Boolean
        get() = getOrDefault().memorizeTypeFullWords
        set(value) = update { copy(memorizeTypeFullWords = value) }

    var memorizeWordVisibility: String
        get() = getOrDefault().memorizeWordVisibility
        set(value) = update { copy(memorizeWordVisibility = value) }

    var memorizeErrorHeatmap: Boolean
        get() = getOrDefault().memorizeErrorHeatmap
        set(value) = update { copy(memorizeErrorHeatmap = value) }

    var memorizeScrambleHideUsed: Boolean
        get() = getOrDefault().memorizeScrambleHideUsed
        set(value) = update { copy(memorizeScrambleHideUsed = value) }

    var memorizeIncludeReference: Boolean
        get() = getOrDefault().memorizeIncludeReference
        set(value) = update { copy(memorizeIncludeReference = value) }

    var activeCycle: Int
        get() = getOrDefault().activeCycle
        set(value) = update { copy(activeCycle = value) }

    fun getBundle(): ReadingProgressSettingsBundle {
        val s = getOrDefault()
        return ReadingProgressSettingsBundle(
            autoMarkMemorized = s.autoMarkMemorized,
            memorizeTypeFullWords = s.memorizeTypeFullWords,
            memorizeWordVisibility = s.memorizeWordVisibility,
            memorizeErrorHeatmap = s.memorizeErrorHeatmap,
            memorizeScrambleHideUsed = s.memorizeScrambleHideUsed,
            memorizeIncludeReference = s.memorizeIncludeReference,
        )
    }

    fun setBundle(bundle: ReadingProgressSettingsBundle) {
        update {
            copy(
                autoMarkMemorized = bundle.autoMarkMemorized,
                memorizeTypeFullWords = bundle.memorizeTypeFullWords,
                memorizeWordVisibility = bundle.memorizeWordVisibility,
                memorizeErrorHeatmap = bundle.memorizeErrorHeatmap,
                memorizeScrambleHideUsed = bundle.memorizeScrambleHideUsed,
                memorizeIncludeReference = bundle.memorizeIncludeReference,
            )
        }
    }

    fun setBundleFromJson(json: String) {
        val bundle = Json.decodeFromString<ReadingProgressSettingsBundle>(json)
        setBundle(bundle)
    }

    fun getBundleAsJson(): String {
        return Json.encodeToString(ReadingProgressSettingsBundle.serializer(), getBundle())
    }
}
