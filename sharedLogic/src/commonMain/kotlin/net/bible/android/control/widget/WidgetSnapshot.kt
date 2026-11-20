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
package net.bible.android.control.widget

import kotlinx.serialization.Serializable

/**
 * What the iOS widget extension reads from the App Group container. The extension cannot load the Kotlin framework
 * or SWORD modules, so every text it shows is prepared here. Bump [version] when a field changes meaning; the
 * extension ignores a snapshot with a version it does not know.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int = VERSION,
    val generatedAt: Long,
    val speak: SpeakSnapshot,
    val listening: List<ListeningRowSnapshot>,
    val labels: List<LabelOption>,
    val bibles: List<BibleOption>,
    val defaultBible: String?,
    val pools: List<VersePool>,
    val strings: Map<String, String>,
) {
    companion object { const val VERSION = 1 }
}

@Serializable
data class SpeakSnapshot(
    /** `SPEAKING`, `PAUSED` or `STOPPED`. */
    val phase: String,
    val title: String?,
    val reference: String?,
    val translation: String?,
    val percent: Int?,
    val verseText: String,
    val nextVerseText: String?,
    val statusLine: String,
    val sleepMinutesLeft: Int?,
    val autoBookmarkOn: Boolean,
)

@Serializable
data class ListeningRowSnapshot(
    val bookmarkId: String,
    val reference: String,
    val translation: String,
    val repeats: Boolean,
    val percent: Int?,
    val current: Boolean,
)

@Serializable data class LabelOption(val id: String, val name: String)

@Serializable data class BibleOption(val initials: String, val abbreviation: String)

/**
 * Upcoming verses of one source in one Bible. [sourceKey] is `label:<id>`, `random:<PART>` (`WHOLE_BIBLE`,
 * `NEW_TESTAMENT`, `PSALMS_PROVERBS`) or `plan`.
 */
@Serializable
data class VersePool(val sourceKey: String, val bible: String, val verses: List<PooledVerse>)

@Serializable data class PooledVerse(val reference: String, val text: String, val link: String)

/** Builds the [WidgetSnapshot] the iOS app writes; pure apart from reading [VerseCandidates]. */
object WidgetSnapshotBuilder {
    /** Most verses in one pool. */
    const val POOL_CAP = 30

    /**
     * Pools: every user label and every random part in the default Bible and in each of [requestedBibles] (installed
     * ones only), plus the reading plan. A source without verses (empty label, no plan) gets no pool, so the
     * extension shows its "no verses" state rather than an empty list.
     */
    suspend fun build(
        state: SpeakWidgetState,
        candidates: VerseCandidates,
        strings: WidgetStrings,
        requestedBibles: Set<String>,
        now: Long,
    ): WidgetSnapshot {
        val installed = candidates.bibles()
        val default = candidates.defaultBible()
        val bibleSet = buildList {
            default?.let { add(it) }
            installed.filter { it.initials in requestedBibles && it.initials != default?.initials }.forEach { add(it) }
        }
        val labels = candidates.labels()
        val pools = ArrayList<VersePool>()
        suspend fun addPool(key: String, source: VerseSource, bible: VerseBible) {
            val verses = candidates.sample(source, bible, POOL_CAP).map { PooledVerse(it.reference, it.text, it.link) }
            if (verses.isNotEmpty()) pools += VersePool(key, bible.initials, verses)
        }
        for (bible in bibleSet) {
            for ((id, _) in labels) addPool("label:$id", VerseSource.Label(id), bible)
            for (part in VerseSource.RandomPart.entries) addPool("random:${part.name}", VerseSource.Random(part), bible)
            addPool("plan", VerseSource.ReadingPlan, bible)
        }
        return WidgetSnapshot(
            generatedAt = now,
            speak = SpeakSnapshot(
                phase = state.phase.name, title = state.title, reference = state.reference,
                translation = state.translation, percent = state.percent, verseText = state.verseText,
                nextVerseText = state.nextVerseText, statusLine = state.statusLine,
                sleepMinutesLeft = state.sleepMinutesLeft, autoBookmarkOn = state.autoBookmarkOn,
            ),
            listening = state.listening.map {
                ListeningRowSnapshot(it.bookmarkId, it.reference, it.translation, it.repeats, it.percent, it.current)
            },
            labels = labels.map { (id, name) -> LabelOption(id.toString(), name) },
            bibles = installed.map { BibleOption(it.initials, it.abbreviation) },
            defaultBible = default?.initials,
            pools = pools,
            strings = strings.asMap(),
        )
    }
}
