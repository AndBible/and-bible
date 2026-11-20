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

import net.bible.android.database.IdType
import net.bible.service.common.andBibleUrl

/** An installed Bible as the widget logic sees it. */
data class VerseBible(val initials: String, val abbreviation: String, val v11nName: String)

/** A user label with its Bible bookmarks as verse refs, ordered by verse ordinal. */
data class LabelVerses(val name: String, val refs: List<VerseRef>)

/**
 * The data the Verse widget reads from the app: bookmarks, reading plan, installed Bibles and verse text. A port so the
 * selection logic runs in plain unit tests; [ControlVerseWidgetSources] is the real implementation.
 */
interface VerseWidgetSources {
    /** The Bible of the active window of the current workspace; null when no Bible is installed. */
    suspend fun defaultBible(): VerseBible?

    /** The installed Bible with [initials], or null. */
    suspend fun bible(initials: String): VerseBible?

    /** The installed Bibles, for configuration lists. */
    suspend fun bibles(): List<VerseBible>

    /** The user labels offered as verse sources (id, name). */
    suspend fun labels(): List<Pair<IdType, String>>

    /** The label's Bible bookmarks, or null when the label no longer exists. Generic bookmarks are ignored. */
    suspend fun label(labelId: IdType): LabelVerses?

    /** The ordinals of every verse of [part] in [bible]'s versification. */
    suspend fun partOrdinals(part: VerseSource.RandomPart, bible: VerseBible): IntRange

    /** First passage of today's reading in the current plan (whole passage), or null without an active plan. */
    suspend fun readingPlanPassage(bible: VerseBible): VerseRef?

    /** Plain text of the verse or range in [bible]; empty when the verse has no text there. */
    suspend fun text(ref: VerseRef, bible: VerseBible): String

    /** Localized, human readable reference, e.g. "Psalm 23:1-3". */
    fun referenceName(ref: VerseRef, bible: VerseBible): String

    /** OSIS reference string for the link. */
    fun osisRef(ref: VerseRef, bible: VerseBible): String

    /** Localized name of a non-label source ("Random verse", "Today's reading"...). */
    fun sourceName(source: VerseSource): String

    /** The date shown with the verse, e.g. "Fri 20 Nov". */
    fun formatDate(epochMs: Long): String
}

/** A verse of a pool: reference, text and link. */
data class PooledVerseText(val ref: VerseRef, val reference: String, val text: String, val link: String)

/**
 * Candidate verses per source and the content a Verse widget shows. Selection is [VerseSelector]: the same widget
 * shows the same verse for a whole period.
 */
class VerseCandidates(private val sources: VerseWidgetSources) {

    /**
     * The verses [source] offers in [bible], in stable order. A random source lists every verse of its part; prefer
     * [content] for those, which never builds the list.
     */
    suspend fun candidates(source: VerseSource, bible: VerseBible): List<VerseRef> = when (source) {
        is VerseSource.Label -> sources.label(source.labelId)?.refs.orEmpty()
        is VerseSource.Random -> sources.partOrdinals(source.part, bible)
            .map { VerseRef(bible.initials, bible.v11nName, it, it) }
        VerseSource.ReadingPlan -> listOfNotNull(sources.readingPlanPassage(bible)?.clampToVerses(READING_PLAN_VERSES))
    }

    suspend fun content(config: VerseWidgetConfig, nowEpochMs: Long, zone: LocalZone): VerseWidgetContent {
        val bible = config.bibleInitials?.let { sources.bible(it) } ?: sources.defaultBible()
            ?: return VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.NO_BIBLE)
        val period = VerseSelector.periodIndex(nowEpochMs, zone, config.frequency)

        val sourceName: String
        val size: Int
        val refAt: suspend (Int) -> VerseRef
        val emptyReason: VerseWidgetContent.Unavailable.Reason
        when (val source = config.source) {
            is VerseSource.Label -> {
                val label = sources.label(source.labelId)
                    ?: return VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_MISSING)
                sourceName = label.name
                size = label.refs.size
                refAt = { label.refs[it] }
                emptyReason = VerseWidgetContent.Unavailable.Reason.LABEL_EMPTY
            }
            is VerseSource.Random -> {
                val ordinals = sources.partOrdinals(source.part, bible)
                sourceName = sources.sourceName(source)
                size = if (ordinals.isEmpty()) 0 else ordinals.last - ordinals.first + 1
                refAt = { VerseRef(bible.initials, bible.v11nName, ordinals.first + it, ordinals.first + it) }
                emptyReason = VerseWidgetContent.Unavailable.Reason.NO_BIBLE
            }
            VerseSource.ReadingPlan -> {
                val passage = sources.readingPlanPassage(bible)?.clampToVerses(READING_PLAN_VERSES)
                    ?: return VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.NO_READING_PLAN)
                sourceName = sources.sourceName(source)
                size = 1
                refAt = { passage }
                emptyReason = VerseWidgetContent.Unavailable.Reason.NO_READING_PLAN
            }
        }
        val start = VerseSelector.pick(size, config.seed, period, config.shuffleCount)
            ?: return VerseWidgetContent.Unavailable(emptyReason)
        // A verse without text in this Bible is skipped: retry the next index, at most MAX_SKIPS times.
        for (step in 0 until minOf(MAX_SKIPS, size)) {
            val ref = refAt((start + step) % size)
            val text = sources.text(ref, bible).trim()
            if (text.isEmpty()) continue
            return VerseWidgetContent.Verse(
                ref = ref,
                reference = sources.referenceName(ref, bible),
                text = text,
                bibleAbbreviation = bible.abbreviation,
                sourceName = sourceName,
                periodLabel = sources.formatDate(nowEpochMs),
                link = andBibleUrl(sources.osisRef(ref, bible), bible.initials, ref.v11nName, ref.startOrdinal),
            )
        }
        return VerseWidgetContent.Unavailable(emptyReason)
    }

    /** The installed Bibles, for configuration lists and pools. */
    suspend fun bibles(): List<VerseBible> = sources.bibles()

    /** The Bible a widget without a chosen one shows. */
    suspend fun defaultBible(): VerseBible? = sources.defaultBible()

    /** The user labels a Verse widget can draw from. */
    suspend fun labels(): List<Pair<IdType, String>> = sources.labels()

    /**
     * Up to [limit] verses of [source] in [bible] with their text, for a pool the iOS extension picks from. Pools
     * that fit are returned whole in verse order; a larger one is sampled evenly (label, plan) or by a fixed hash
     * (random), so the sample is stable between snapshots. Verses without text are left out.
     */
    suspend fun sample(source: VerseSource, bible: VerseBible, limit: Int): List<PooledVerseText> {
        val picks: List<VerseRef> = when (source) {
            is VerseSource.Random -> {
                val ordinals = sources.partOrdinals(source.part, bible)
                val size = if (ordinals.isEmpty()) 0 else ordinals.last - ordinals.first + 1
                val seed = source.part.ordinal.toLong() * 7919L + bible.initials.hashCode()
                (0 until limit * 2).mapNotNull { VerseSelector.pick(size, seed, it.toLong(), 0) }
                    .distinct().map { ordinals.first + it }.map { VerseRef(bible.initials, bible.v11nName, it, it) }
            }
            else -> {
                val all = candidates(source, bible)
                if (all.size <= limit) all else List(limit) { all[it * all.size / limit] }
            }
        }
        val result = ArrayList<PooledVerseText>()
        for (ref in picks) {
            if (result.size >= limit) break
            val text = sources.text(ref, bible).trim()
            if (text.isEmpty()) continue
            result += PooledVerseText(
                ref, sources.referenceName(ref, bible), text,
                andBibleUrl(sources.osisRef(ref, bible), bible.initials, ref.v11nName, ref.startOrdinal),
            )
        }
        return result
    }

    private fun VerseRef.clampToVerses(max: Int) = copy(endOrdinal = minOf(endOrdinal, startOrdinal + max - 1))

    companion object {
        const val READING_PLAN_VERSES = 3
        const val MAX_SKIPS = 20
    }
}
