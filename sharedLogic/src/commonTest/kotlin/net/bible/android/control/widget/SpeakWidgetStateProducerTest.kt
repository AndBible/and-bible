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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakWidgetStateProducerTest {
    private class FakeSource(private val clock: () -> Long) : SpeakWidgetSource {
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        override val changes = events
        var sleepAt: Long? = null
        var factsCalls = 0
        var resets = 0
        var facts = SpeakFacts(
            phase = SpeakPhase.SPEAKING, title = "Psalm 23", reference = "Psalm 23:1", translation = "WEB", percent = 34,
            verseText = "The LORD is my shepherd.", nextVerseText = "I shall not want.", autoBookmarkOn = true,
            rows = emptyList(), currentBookmarkId = null,
        )
        override fun sleepActivationTimeMs() = sleepAt
        override fun nowMs() = clock()
        override suspend fun facts(): SpeakFacts { factsCalls++; return facts }
        override fun reset() { resets++ }
    }

    private fun TestScope.producer(strings: WidgetStrings = FakeWidgetStrings(), configure: FakeSource.() -> Unit = {}):
        Pair<SpeakWidgetStateProducer, FakeSource> {
        val source = FakeSource { currentTime }.apply(configure)
        val p = SpeakWidgetStateProducer(backgroundScope, source, strings)
        p.start()
        runCurrent()
        return p to source
    }

    @Test fun speakingStateHasPauseAndStatusLine() = runTest {
        val (p, _) = producer()
        val s = p.state.value
        assertEquals(SpeakPhase.SPEAKING, s.phase)
        assertEquals("Psalm 23:1 · 34 % · WEB", s.statusLine)
        assertEquals("Psalm 23", s.title)
        assertEquals("I shall not want.", s.nextVerseText)
    }

    @Test fun restartingWhilePausedDoesNotForgetThePosition() = runTest {
        val (p, source) = producer { facts = facts.copy(phase = SpeakPhase.PAUSED) }
        p.stop()
        p.start()
        runCurrent()
        assertEquals(0, source.resets)
        assertEquals("Continue from Psalm 23:1 · WEB", p.state.value.statusLine)
    }

    @Test fun stoppedStateSaysContinueFrom() = runTest {
        val (p, _) = producer { facts = facts.copy(phase = SpeakPhase.STOPPED) }
        assertEquals("Continue from Psalm 23:1 · WEB", p.state.value.statusLine)
        assertFalse(p.state.value.statusLine.contains("Stopped", ignoreCase = true))
    }

    @Test fun stoppedWithoutPositionSaysContinueListening() = runTest {
        val (p, _) = producer { facts = facts.copy(phase = SpeakPhase.STOPPED, reference = null, translation = null, percent = null) }
        assertEquals("Continue listening", p.state.value.statusLine)
    }

    @Test fun sleepTimerMinutesRoundUp() = runTest {
        val (p, _) = producer { sleepAt = 17 * 60_000L + 10_000L }
        assertEquals(18, p.state.value.sleepMinutesLeft)
    }

    @Test fun sleepTimerOffIsNull() = runTest {
        val (p, _) = producer()
        assertNull(p.state.value.sleepMinutesLeft)
    }

    @Test fun burstOfTenProgressEventsEmitsOnce() = runTest {
        val (p, source) = producer()
        val before = source.factsCalls
        repeat(10) { source.events.tryEmit(Unit); advanceTimeBy(40) }
        advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1)
        runCurrent()
        assertEquals(1, source.factsCalls - before)
    }

    @Test fun spacedEventsEachEmit() = runTest {
        val (_, source) = producer()
        val before = source.factsCalls
        repeat(3) { source.events.tryEmit(Unit); advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 100) }
        runCurrent()
        assertEquals(3, source.factsCalls - before)
    }

    @Test fun tickerRefreshesMinutesWhileTimerRuns() = runTest {
        val (p, _) = producer { sleepAt = 5 * 60_000L }
        assertEquals(5, p.state.value.sleepMinutesLeft)
        advanceTimeBy(SpeakWidgetStateProducer.TICK_MS + SpeakWidgetStateProducer.COALESCE_MS + 10)
        runCurrent()
        assertEquals(4, p.state.value.sleepMinutesLeft)
    }

    @Test fun noTickerEmissionsWhileTimerIsOff() = runTest {
        val (_, source) = producer()
        val before = source.factsCalls
        advanceTimeBy(5 * SpeakWidgetStateProducer.TICK_MS)
        runCurrent()
        assertEquals(0, source.factsCalls - before)
    }

    private fun row(id: String, percent: Int? = 10) = ListeningRow(id, "Ref $id", "WEB", false, percent, false)

    @Test fun listeningRowsMarkCurrent() = runTest {
        val (p, _) = producer { facts = facts.copy(rows = listOf(row("a"), row("b"), row("c")), currentBookmarkId = "b") }
        assertEquals(listOf(false, true, false), p.state.value.listening.map { it.current })
    }

    @Test fun genericBookmarkRowHasNullPercent() = runTest {
        val (p, _) = producer { facts = facts.copy(rows = listOf(row("g", percent = null))) }
        assertNull(p.state.value.listening.single().percent)
        assertTrue(p.state.value.listening.none { it.current })
    }

    @Test fun localeRefreshRebuildsStrings() = runTest {
        var strings: WidgetStrings = FakeWidgetStrings()
        val delegating = object : WidgetStrings by FakeWidgetStrings() {
            override fun statusLine(reference: String, percent: Int, translation: String) = strings.statusLine(reference, percent, translation)
        }
        val (p, _) = producer(delegating)
        assertEquals("Psalm 23:1 · 34 % · WEB", p.state.value.statusLine)
        strings = FakeWidgetStrings(tag = " [fi]")
        p.refreshForLocale()
        runCurrent()
        assertEquals("Psalm 23:1 · 34 % · WEB [fi]", p.state.value.statusLine)
    }

    @Test fun failingReadKeepsPreviousStateAndRecovers() = runTest {
        val source = FakeSource { currentTime }
        var fail = false
        val failing = object : SpeakWidgetSource by source {
            override suspend fun facts() = if (fail) error("db gone") else source.facts()
        }
        val p = SpeakWidgetStateProducer(backgroundScope, failing, FakeWidgetStrings())
        p.start(); runCurrent()
        val good = p.state.value
        fail = true
        source.events.tryEmit(Unit); advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1); runCurrent()
        assertEquals(good, p.state.value)
        fail = false
        source.facts = source.facts.copy(reference = "John 3:16")
        source.events.tryEmit(Unit); advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1); runCurrent()
        assertEquals("John 3:16", p.state.value.reference)
    }

    @Test fun startAgainAfterScopeChildrenWereCancelled() = runTest {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val source = FakeSource { currentTime }
        val p = SpeakWidgetStateProducer(scope, source, FakeWidgetStrings())
        p.start(); runCurrent()
        val first = source.factsCalls
        // what DatabaseResetter does to the app scope
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
        runCurrent()
        p.start(); runCurrent()
        assertTrue(source.factsCalls > first, "start() must rebuild after the previous run died")
        source.facts = source.facts.copy(reference = "John 3:16")
        source.events.tryEmit(Unit); advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1); runCurrent()
        assertEquals("John 3:16", p.state.value.reference)
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
    }

    @Test fun startWhileRunningIsIdempotentAndStopEndsFollowing() = runTest {
        val (p, source) = producer()
        p.start(); runCurrent()
        val before = source.factsCalls
        source.events.tryEmit(Unit); advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1); runCurrent()
        assertEquals(1, source.factsCalls - before)
        p.stop()
        source.events.tryEmit(Unit); advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1); runCurrent()
        assertEquals(1, source.factsCalls - before)
        p.start(); runCurrent()
        assertTrue(source.factsCalls - before > 1)
    }

    @Test fun tickerStartsWhenTimerIsSetLater() = runTest {
        val (p, source) = producer()
        source.sleepAt = 10 * 60_000L
        source.events.tryEmit(Unit)
        advanceTimeBy(SpeakWidgetStateProducer.COALESCE_MS + 1); runCurrent()
        assertEquals(10, p.state.value.sleepMinutesLeft)
        advanceTimeBy(SpeakWidgetStateProducer.TICK_MS + 10); runCurrent()
        assertEquals(9, p.state.value.sleepMinutesLeft)
    }

    @Test fun tickerStopsWhenTimerIsCleared() = runTest {
        val (_, source) = producer { sleepAt = 10 * 60_000L }
        source.sleepAt = null
        advanceTimeBy(SpeakWidgetStateProducer.TICK_MS + 10); runCurrent() // last tick sees it off and ends the loop
        val before = source.factsCalls
        advanceTimeBy(5 * SpeakWidgetStateProducer.TICK_MS); runCurrent()
        assertEquals(0, source.factsCalls - before)
    }
}
