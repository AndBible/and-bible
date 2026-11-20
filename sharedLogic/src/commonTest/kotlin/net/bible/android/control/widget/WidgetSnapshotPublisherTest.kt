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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.database.IdType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotPublisherTest {
    private val sources = FakeVerseSources()
    private val strings = FakeWidgetStrings()
    private val stopped = SpeakWidgetState(
        phase = SpeakPhase.STOPPED, title = null, reference = null, translation = null, percent = null, verseText = "",
        nextVerseText = null, statusLine = "Continue listening", sleepMinutesLeft = null, listening = emptyList(),
        autoBookmarkOn = false,
    )
    private val state = MutableStateFlow(stopped)
    private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val written = mutableListOf<WidgetSnapshot>()
    private val writtenPools = mutableListOf<WidgetPools>()
    private val reloadKinds = mutableListOf<WidgetReload>()
    private val reloads get() = reloadKinds.size
    private var requested = emptySet<String>()

    private fun TestScope.publisher() = WidgetSnapshotPublisher(
        scope = backgroundScope, state = state, candidates = VerseCandidates(sources), strings = strings,
        requestedBibles = { requested }, zone = LocalZone { 0L }, nowMs = { currentTime },
        poolInvalidations = invalidations, write = { written += it }, writePools = { writtenPools += it },
        reload = { reloadKinds += it },
    )

    private fun speaking(percent: Int) = stopped.copy(phase = SpeakPhase.SPEAKING, percent = percent, reference = "John 3:16")

    @Test fun firstSnapshotIsWrittenAndTimelinesReloaded() = runTest {
        sources.labelMap[IdType()] = LabelVerses("Memory", listOf(VerseRef("WEB", "KJV", 10, 10)))
        publisher().start(); runCurrent()
        assertEquals(1, written.size)
        assertEquals(1, reloads)
        assertTrue(writtenPools.single().pools.isNotEmpty())
        assertEquals(listOf(WidgetReload.ALL), reloadKinds)
    }

    @Test fun speakUpdatesReuseThePools() = runTest {
        sources.labelMap[IdType()] = LabelVerses("Memory", listOf(VerseRef("WEB", "KJV", 10, 10)))
        val p = publisher(); p.start(); runCurrent()
        for (percent in 1..5) { state.value = speaking(percent); advanceTimeBy(1_100); runCurrent() }
        assertEquals(6, written.size)
        assertEquals(1, p.poolBuilds, "pools must not be rebuilt on a speak update")
        assertEquals(1, writtenPools.size, "the pools file is written once, not on every speak update")
    }

    @Test fun writesAreAtLeastOneSecondApart() = runTest {
        val p = publisher(); p.start(); runCurrent()
        val first = currentTime
        state.value = speaking(1); runCurrent()
        state.value = speaking(2); advanceTimeBy(500); runCurrent()
        assertEquals(1, written.size, "second write must wait for the interval")
        advanceTimeBy(600); runCurrent()
        assertEquals(2, written.size, "the conflated latest state is written after the interval")
        assertEquals(2, written.last().speak.percent)
        assertTrue(currentTime - first >= 1_000)
    }

    @Test fun newLabelRebuildsPools() = runTest {
        val p = publisher(); p.start(); runCurrent()
        assertEquals(1, p.poolBuilds)
        val id = IdType()
        sources.labelMap[id] = LabelVerses("New", listOf(VerseRef("WEB", "KJV", 3, 3)))
        state.value = speaking(1); advanceTimeBy(1_100); runCurrent()
        assertEquals(2, p.poolBuilds)
        assertTrue(writtenPools.last().pools.any { it.sourceKey == "label:$id" })
    }

    @Test fun bookmarkInvalidationRebuildsPools() = runTest {
        val id = IdType()
        sources.labelMap[id] = LabelVerses("L", listOf(VerseRef("WEB", "KJV", 3, 3)))
        val p = publisher(); p.start(); runCurrent()
        sources.labelMap[id] = LabelVerses("L", listOf(VerseRef("WEB", "KJV", 4, 4)))
        invalidations.tryEmit(Unit); runCurrent()
        state.value = speaking(1); advanceTimeBy(1_100); runCurrent()
        assertEquals(2, p.poolBuilds)
        assertEquals("Ref 4", writtenPools.last().pools.single { it.sourceKey == "label:$id" }.verses.single().reference)
    }

    @Test fun requestedBibleRebuildsPoolsOnWriteNow() = runTest {
        sources.installed["KJV2"] = VerseBible("KJV2", "KJV", "KJV")
        val p = publisher(); p.start(); runCurrent()
        assertEquals(setOf("WEB"), writtenPools.last().pools.map { it.bible }.toSet())
        requested = setOf("KJV2")
        p.writeNow(); runCurrent()
        assertEquals(setOf("WEB", "KJV2"), writtenPools.last().pools.map { it.bible }.toSet())
    }

    @Test fun unchangedSnapshotIsNotWrittenAgain() = runTest {
        val p = publisher(); p.start(); runCurrent()
        advanceTimeBy(5_000)
        p.writeNow(); runCurrent()
        assertEquals(1, written.size)
        assertEquals(1, reloads)
    }

    @Test fun failedWriteIsRetriedNotRemembered() = runTest {
        var failOnce = true
        val p = WidgetSnapshotPublisher(
            scope = backgroundScope, state = state, candidates = VerseCandidates(sources), strings = strings,
            requestedBibles = { requested }, zone = LocalZone { 0L }, nowMs = { currentTime },
            poolInvalidations = invalidations,
            write = { if (failOnce) { failOnce = false; error("container unavailable") } else written += it },
            writePools = { writtenPools += it },
            reload = { reloadKinds += it },
        )
        p.start(); runCurrent()
        assertEquals(0, written.size)
        assertEquals(0, reloads, "no reload for a snapshot that was not written")
        p.writeNow(); runCurrent()
        assertEquals(1, written.size)
    }

    @Test fun weekChangeRebuildsPools() = runTest {
        sources.labelMap[IdType()] = LabelVerses("L", (0 until 100).map { VerseRef("WEB", "KJV", it, it) })
        val p = publisher(); p.start(); runCurrent()
        advanceTimeBy(8L * 24 * 3_600_000)
        p.writeNow(); runCurrent()
        assertEquals(2, p.poolBuilds)
    }

    @Test fun speakOnlyUpdatesReloadOnlySpeakKinds() = runTest {
        sources.labelMap[IdType()] = LabelVerses("Memory", listOf(VerseRef("WEB", "KJV", 10, 10)))
        publisher().start(); runCurrent()
        for (percent in 1..3) { state.value = speaking(percent); advanceTimeBy(1_100); runCurrent() }
        assertEquals(listOf(WidgetReload.ALL, WidgetReload.SPEAK, WidgetReload.SPEAK, WidgetReload.SPEAK), reloadKinds)
    }

    @Test fun labelChangeReloadsAllKinds() = runTest {
        publisher().start(); runCurrent()
        sources.labelMap[IdType()] = LabelVerses("New", listOf(VerseRef("WEB", "KJV", 3, 3)))
        state.value = speaking(1); advanceTimeBy(1_100); runCurrent()
        assertEquals(WidgetReload.ALL, reloadKinds.last())
        assertEquals(2, writtenPools.size)
    }

    @Test fun snapshotJsonCarriesNoPools() = runTest {
        sources.labelMap[IdType()] = LabelVerses("Memory", listOf(VerseRef("WEB", "KJV", 10, 10)))
        publisher().start(); runCurrent()
        assertTrue(!kotlinx.serialization.json.Json.encodeToString(WidgetSnapshot.serializer(), written.single()).contains("pools"))
    }

    @Test fun failedPoolsWriteIsRetried() = runTest {
        var failOnce = true
        val p = WidgetSnapshotPublisher(
            scope = backgroundScope, state = state, candidates = VerseCandidates(sources), strings = strings,
            requestedBibles = { requested }, zone = LocalZone { 0L }, nowMs = { currentTime },
            poolInvalidations = invalidations, write = { written += it },
            writePools = { if (failOnce) { failOnce = false; error("disk full") } else writtenPools += it },
            reload = { reloadKinds += it },
        )
        p.start(); runCurrent()
        assertEquals(0, written.size, "no snapshot is written before its pools")
        p.writeNow(); runCurrent()
        assertEquals(1, writtenPools.size)
        assertEquals(1, written.size)
    }

    @Test fun restartAfterStopFollowsStateAgain() = runTest {
        val p = publisher(); p.start(); runCurrent(); p.stop()
        state.value = speaking(7); advanceTimeBy(1_100); runCurrent()
        assertEquals(1, written.size)
        p.start(); runCurrent()
        assertEquals(7, written.last().speak.percent)
    }
}
