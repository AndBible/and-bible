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

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.bible.android.database.IdType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetSnapshotTest {
    private val sources = FakeVerseSources()
    private val candidates = VerseCandidates(sources)
    private val strings = FakeWidgetStrings()
    private val state = SpeakWidgetState(
        phase = SpeakPhase.PAUSED, title = "Psalm 23", reference = "Psalm 23:1", translation = "WEB", percent = 34,
        verseText = "The LORD is my shepherd.", nextVerseText = null, statusLine = "Continue from Psalm 23:1 · WEB",
        sleepMinutesLeft = 18, autoBookmarkOn = true,
        listening = listOf(ListeningRow("b1", "John 3:16", "WEB", true, 12, true), ListeningRow("g1", "Pilgrim", "PP", false, null, false)),
    )

    private suspend fun build(requested: Set<String> = emptySet()) =
        WidgetSnapshotBuilder.build(state, candidates, strings, requested, now = 1_000L, zone = LocalZone { 0L })

    @Test fun snapshotRoundTripsJson() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Memory", listOf(VerseRef("WEB", "KJV", 10, 10)))
        val snap = build()
        val json = Json.encodeToString(WidgetSnapshot.serializer(), snap)
        assertTrue(json.contains("\"version\":1"), json)
        assertEquals(snap, Json.decodeFromString(WidgetSnapshot.serializer(), json))
        assertEquals("PAUSED", snap.speak.phase)
        assertEquals(listOf(true, false), snap.listening.map { it.current })
        assertEquals(null, snap.listening[1].percent)
    }

    @Test fun poolsCappedAtThirty() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Big", (0 until 100).map { VerseRef("WEB", "KJV", it, it) })
        val snap = build()
        val pool = snap.pools.single { it.sourceKey == "label:$id" }
        assertEquals(30, pool.verses.size)
        assertTrue(snap.pools.all { it.verses.size <= 30 })
        assertEquals(30, pool.verses.map { it.reference }.toSet().size)
    }

    @Test fun poolsCoverEveryLabelAndRandomPart() = runTest {
        val a = IdType(); val b = IdType()
        sources.labelMap[a] = LabelVerses("A", listOf(VerseRef("WEB", "KJV", 1, 1)))
        sources.labelMap[b] = LabelVerses("B", listOf(VerseRef("WEB", "KJV", 2, 2)))
        sources.plan = VerseRef("WEB", "KJV", 5, 9)
        val keys = build().pools.map { it.sourceKey }.toSet()
        val expected = setOf("label:$a", "label:$b", "plan") + VerseSource.RandomPart.entries.map { "random:${it.name}" }
        assertEquals(expected, keys)
    }

    @Test fun requestedBibleAddsPools() = runTest {
        val kjv = VerseBible("KJV2", "KJV", "KJV")
        sources.installed["KJV2"] = kjv
        val without = build().pools.map { it.bible }.toSet()
        val with = build(setOf("KJV2")).pools.map { it.bible }.toSet()
        assertEquals(setOf("WEB"), without)
        assertEquals(setOf("WEB", "KJV2"), with)
    }

    @Test fun notInstalledRequestedBibleIsIgnored() = runTest {
        assertEquals(setOf("WEB"), build(setOf("GONE")).pools.map { it.bible }.toSet())
    }

    @Test fun stringsContainAllWidgetKeys() = runTest {
        val snap = build()
        WidgetStringKeys.all.forEach { assertTrue(it in snap.strings, "missing $it") }
        assertEquals("Speak", snap.strings[WidgetStringKeys.SPEAK_NAME])
        assertEquals(WidgetStringKeys.all.size, snap.strings.size)
    }

    @Test fun deletedLabelHasNoPool() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Gone soon", listOf(VerseRef("WEB", "KJV", 1, 1)))
        assertTrue(build().pools.any { it.sourceKey == "label:$id" })
        sources.labelMap.remove(id)
        val snap = build()
        assertTrue(snap.pools.none { it.sourceKey == "label:$id" })
        assertTrue(snap.labels.none { it.id == id.toString() })
    }

    @Test fun emptyLabelHasNoPool() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("Empty", emptyList())
        assertTrue(build().pools.none { it.sourceKey == "label:$id" })
    }

    @Test fun noBibleGivesNoPools() = runTest {
        sources.default = null
        sources.installed.clear()
        val snap = build()
        assertTrue(snap.pools.isEmpty())
        assertEquals(null, snap.defaultBible)
    }
}
