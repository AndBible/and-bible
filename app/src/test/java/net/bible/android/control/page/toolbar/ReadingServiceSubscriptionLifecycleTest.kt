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
package net.bible.android.control.page.toolbar

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.buildSharedBindings
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPlaybackState
import net.bible.android.control.speak.SpeakSettingsServiceImpl
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the production bindings, but restarts only the two services' owning Koin container. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingServiceSubscriptionLifecycleTest {
    private lateinit var repository: WindowRepository
    private lateinit var windowControl: WindowControl
    private lateinit var app: KoinApplication

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        repository = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repository
        windowControl.activeWindowPageManager.currentBible.setCurrentDocumentAndKey(
            PassageTestData.ESV, PassageTestData.PS_139_2,
        )
        app = startServices()
    }

    private fun startServices(): KoinApplication {
        val host = GlobalContext.get()
        return koinApplication {
            modules(buildSharedBindings(), module {
                // Keep the real host collaborators alive across the service-container restart.
                single { windowControl }
                single { host.get<DocumentControl>() }
                single { host.get<PageControl>() }
                single { host.get<SpeakControl>() }
                single { host.get<WindowStateServiceImpl>() }
                single { host.get<CloudSync>() }
                single { host.get<AdvancedSpeakSettings>() }
            })
        }
    }

    @After
    fun tearDown() {
        app.close()
        DatabaseResetter.resetDatabase(repository.scope)
    }

    private fun assertToolbarSubscriptionEnds(emit: () -> Unit) {
        val old = app.koin.get<ToolbarStateService>() as ToolbarStateServiceImpl
        old.refresh()
        val frozen = old.toolbar.value
        app.close()
        app = startServices()
        val current = app.koin.get<ToolbarStateService>()
        assertNotSame(old, current)
        current.refresh()
        // A silent model change makes delivery observable even for events that rebuild the same page.
        repository.workspaceSettings.workspaceColor = 0xFF123456.toInt()
        emit()
        assertEquals(0xFF123456.toInt(), current.toolbar.value.workspaceColorArgb)
        assertEquals("Closed toolbar must not receive the event", frozen, old.toolbar.value)
    }

    @Test
    fun passageSubscriptionEndsOnKoinRestart() = assertToolbarSubscriptionEnds {
        PassageChangeMediator.contentChangeFinished()
    }

    @Test
    fun windowSubscriptionEndsOnKoinRestart() = assertToolbarSubscriptionEnds {
        GlobalContext.get().get<WindowStateServiceImpl>().notify(WindowChange.ActiveWindowChanged(repository.activeWindow))
    }

    @Test
    fun workspaceSubscriptionEndsOnKoinRestart() = assertToolbarSubscriptionEnds {
        WorkspaceChanges.notifyColorEdited()
    }

    @Test
    fun speakSubscriptionEndsOnKoinRestart() = assertToolbarSubscriptionEnds {
        SpeakChanges.notifyState(SpeakPlaybackState.SILENT)
    }

    @Test
    fun syncSubscriptionEndsOnKoinRestart() {
        val old = app.koin.get<ToolbarStateService>()
        val frozen = old.toolbar.value
        app.close()
        app = startServices()
        val current = app.koin.get<ToolbarStateService>()
        GlobalContext.get().get<CloudSync>().notifySyncRunning(!frozen.syncRunning)
        assertEquals(!frozen.syncRunning, current.toolbar.value.syncRunning)
        assertEquals(frozen, old.toolbar.value)
    }

    @Test
    fun speakSettingsSubscriptionEndsOnKoinRestart() {
        val old = app.koin.get<SpeakSettingsService>() as SpeakSettingsServiceImpl
        val frozen = old.playback.value
        app.close()
        app = startServices()
        val current = app.koin.get<SpeakSettingsService>()
        assertNotSame(old, current)
        val settings = SpeakSettings.load()
        try {
            val changed = settings.makeCopy()
            changed.playbackSettings = changed.playbackSettings.copy(speed = frozen.speedPercent + 10)
            changed.save()
            assertEquals(frozen.speedPercent + 10, current.playback.value.speedPercent)
            assertEquals("Closed settings service must not reload playback", frozen, old.playback.value)
        } finally {
            settings.save()
        }
    }
}
