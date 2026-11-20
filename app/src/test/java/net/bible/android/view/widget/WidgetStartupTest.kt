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
package net.bible.android.view.widget

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.SpeakWidgetManager
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.widget.ControlSpeakWidgetSource
import net.bible.android.control.widget.SpeakPhase
import net.bible.android.control.widget.SpeakWidgetStateProducer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The speak widget state producer must run only while a speak widget exists, and must not create the TTS manager (#184). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WidgetStartupTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val koin get() = KoinPlatform.getKoin()
    private val originalHook = WidgetUpdater.hasSpeakWidgets

    @Before fun setUp() {
        SpeakWidgetManager.instance?.destroy()
        WidgetUpdater.stop()
    }

    @After fun tearDown() {
        WidgetUpdater.hasSpeakWidgets = originalHook
        WidgetUpdater.stop()
    }

    @Test fun coldStartWithoutASpeakWidgetNeitherStartsTheProducerNorCreatesTheTtsManager() = runBlocking {
        WidgetUpdater.hasSpeakWidgets = { false }
        val scopeJob = koin.get<net.bible.sharedcore.platform.AppCoroutineScope>().coroutineContext[kotlinx.coroutines.Job]!!
        val before = scopeJob.children.toSet()
        SpeakWidgetManager() // what startPlatformServices does on Main at every cold start
        // Join exactly the start-decision job the manager launched: deterministic, no sleeping.
        val decision = scopeJob.children.toSet() - before
        assertTrue("the start decision job was not launched", decision.isNotEmpty())
        assertNotNull("the start decision never finished", withTimeoutOrNull(10_000) { decision.forEach { it.join() }; true })
        assertFalse(koin.get<SpeakWidgetStateProducer>().isRunning)
        assertFalse(koin.get<SpeakControl>().ttsStarted)
    }

    @Test fun coldStartWithASpeakWidgetStartsTheProducer() = runBlocking {
        WidgetUpdater.hasSpeakWidgets = { true }
        SpeakWidgetManager()
        val producer = koin.get<SpeakWidgetStateProducer>()
        assertTrue(withTimeoutOrNull(10_000) { while (!producer.isRunning) kotlinx.coroutines.delay(20); true } == true)
    }

    @Test fun factsWithoutATtsManagerAreStoppedAndDoNotCreateOne() = runBlocking {
        val speak = koin.get<SpeakControl>()
        assertFalse(speak.ttsStarted)
        val facts = koin.get<ControlSpeakWidgetSource>().facts()
        assertEquals(SpeakPhase.STOPPED, facts.phase)
        assertFalse("facts() created the TTS manager", speak.ttsStarted)
    }

    @Test fun producerStartsWhenAWidgetIsAddedAndStopsAfterTheLastIsRemoved() = runBlocking {
        val producer = koin.get<SpeakWidgetStateProducer>()
        WidgetUpdater.hasSpeakWidgets = { true }
        WidgetUpdater.start(context)
        assertTrue(producer.isRunning)
        WidgetUpdater.stopIfNoSpeakWidgets(context) // one widget remains
        assertTrue(producer.isRunning)
        WidgetUpdater.hasSpeakWidgets = { false }
        WidgetUpdater.stopIfNoSpeakWidgets(context)
        assertFalse(producer.isRunning)
    }

    @Test fun defaultDetectionSeesPlacedWidgetsOfLegacyReceiverClassesWithoutGlanceMapping() = runBlocking {
        assertFalse(WidgetUpdater.hasSpeakWidgets(context))
        val manager = org.robolectric.Shadows.shadowOf(android.appwidget.AppWidgetManager.getInstance(context))
        manager.createWidget(SpeakWidgetManager.SmallSpeakControlWidget::class.java, android.R.layout.simple_list_item_1)
        assertTrue(WidgetUpdater.hasSpeakWidgets(context))
    }

    @Test fun deletingTheLastWidgetStopsTheProducerWithoutThrowing() = runBlocking {
        val producer = koin.get<SpeakWidgetStateProducer>()
        WidgetUpdater.start(context)
        assertTrue(producer.isRunning)
        WidgetUpdater.hasSpeakWidgets = { false }
        org.robolectric.shadows.ShadowLog.clear()
        run {
            val receiver = SpeakWidgetManager.LargeSpeakControlWidget()
            // As in a real broadcast: one PendingResult, which Glance's onDeleted takes with goAsync() (a second goAsync() is null).
            val create = Class.forName("org.robolectric.shadows.ShadowBroadcastPendingResult").declaredMethods
                .first { it.name == "create" }.apply { isAccessible = true }
            val pending = create.invoke(null, 0, null, null, false)
            android.content.BroadcastReceiver::class.java.getMethod("setPendingResult", android.content.BroadcastReceiver.PendingResult::class.java)
                .invoke(receiver, pending)
            receiver.onDeleted(context, intArrayOf(1))
            assertNotNull(withTimeoutOrNull(10_000) { while (producer.isRunning) kotlinx.coroutines.delay(20); true })
            kotlinx.coroutines.delay(200) // an exception after the stop (the old pending.finish()) lands here
        }
        // AppCoroutineScope logs what escapes a launched job; the old pending.finish() on a null PendingResult did.
        val escaped = org.robolectric.shadows.ShadowLog.getLogsForTag("AppCoroutineScope").mapNotNull { it.throwable }
        assertTrue("escaped: $escaped", escaped.isEmpty())
    }

    @Test fun widgetAddedWhileStopIsDecidingKeepsTheProducerRunning() = runBlocking {
        val producer = koin.get<SpeakWidgetStateProducer>()
        WidgetUpdater.start(context)
        var calls = 0
        // First answer (outside the lock) says none left; the re-check inside the lock sees the new widget.
        WidgetUpdater.hasSpeakWidgets = { calls++ > 0 }
        WidgetUpdater.stopIfNoSpeakWidgets(context)
        assertTrue(producer.isRunning)
    }
}
