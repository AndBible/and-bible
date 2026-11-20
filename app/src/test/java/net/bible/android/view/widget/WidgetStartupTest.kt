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
        // Tests that construct a SpeakWidgetManager must not leave the singleton behind for the next test class in this JVM.
        SpeakWidgetManager.instance?.destroy()
    }

    @Test fun removingTheLastWidgetOfAnAppThatNeverStartedTheProducerDoesNotCreateIt() = runBlocking {
        val original = koin.get<SpeakWidgetStateProducer>()
        var resolutions = 0
        val counting = org.koin.dsl.module { single<SpeakWidgetStateProducer> { resolutions++; original } }
        org.koin.core.context.loadKoinModules(counting)
        try {
            WidgetUpdater.hasSpeakWidgets = { false }
            WidgetUpdater.stopIfNoSpeakWidgets(context) // the producer was never started: nothing to stop, nothing to build
            assertEquals("stop() resolved (built) the speak widget state producer", 0, resolutions)
        } finally {
            org.koin.core.context.unloadKoinModules(counting)
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<SpeakWidgetStateProducer> { original } })
        }
    }

    @Test fun controlSourceReportsSpeakingWhenTheTtsFlagIsTrueAndTheManagerIsSpeaking() = runBlocking {
        val speak = koin.get<SpeakControl>()
        val manager = koin.get<net.bible.service.device.speak.TextToSpeechServiceManager>()
        val flag = SpeakControl::class.java.getDeclaredField("ttsInitialized").apply { isAccessible = true }
        val speaking = net.bible.service.device.speak.TextToSpeechServiceManager::class.java.getDeclaredField("isSpeaking")
            .apply { isAccessible = true } // private set: the manager's own speaking flag
        val (flagBefore, speakingBefore) = flag.getBoolean(speak) to speaking.getBoolean(manager)
        try {
            flag.setBoolean(speak, true)
            speaking.setBoolean(manager, true)
            assertTrue(speak.ttsStarted)
            assertEquals(SpeakPhase.SPEAKING, koin.get<ControlSpeakWidgetSource>().facts().phase)
        } finally { // singletons outlive the test in a shared JVM
            flag.setBoolean(speak, flagBefore)
            speaking.setBoolean(manager, speakingBefore)
        }
    }

    @Test fun coldStartWithoutASpeakWidgetNeitherStartsTheProducerNorCreatesTheTtsManager() = runBlocking {
        // The decision job may finish before a snapshot of the scope's children could see it: observe it through the hook.
        val asked = kotlinx.coroutines.CompletableDeferred<Unit>()
        WidgetUpdater.hasSpeakWidgets = { asked.complete(Unit); false }
        SpeakWidgetManager() // what startPlatformServices does on Main at every cold start
        assertNotNull("the start decision never ran", withTimeoutOrNull(10_000) { asked.await(); true })
        kotlinx.coroutines.delay(300) // the job's only remaining step is the (negative) decision; a start would show now
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
        // Not createWidget: it delivers APPWIDGET_UPDATE to the Glance receiver, whose goAsync/WorkManager work then fails after the test.
        android.appwidget.AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(1, android.content.ComponentName(context, SpeakWidgetManager.SmallSpeakControlWidget::class.java))
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
            receiver.callAsBroadcast { receiver.onDeleted(context, intArrayOf(1)) }
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
