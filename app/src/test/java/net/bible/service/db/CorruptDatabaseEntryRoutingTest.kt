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
package net.bible.service.db

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.StartupActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.AndBibleMediaButtonReceiver
import net.bible.service.device.speak.TextToSpeechNotificationManager
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #4112 review: entry points other than `StartupActivity` meeting damaged user data on a cold process. Headless ones
 * (media button, TTS notification, speak widget) end quietly instead of crashing, and the damage is checked once,
 * not on every access; a screen started directly hands over to the startup's recovery question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CorruptDatabaseEntryRoutingTest {
    private val app get() = ApplicationProvider.getApplicationContext<BibleApplication>()
    private val damaged = CorruptDatabasesException(listOf(CorruptDatabase(BookmarkDatabaseName, "/data/$BookmarkDatabaseName")))
    private var opens = 0
    private var wasInitialized = false
    private var wasReady = false

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasInitialized = CommonUtils.initialized
        wasReady = DatabaseContainer.ready
        CommonUtils.initialized = false
        DatabaseContainer.ready = false
        DatabaseContainer.containerFactory = { opens++; throw damaged }
    }

    @After fun tearDown() {
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.ready = wasReady
        CommonUtils.initialized = wasInitialized
        DatabaseResetter.resetDatabase()
    }

    @Test fun headlessEntriesEndQuietlyAndCheckTheFilesOnce() {
        repeat(2) { AndBibleMediaButtonReceiver().onReceive(app, Intent(Intent.ACTION_MEDIA_BUTTON)) }
        TextToSpeechNotificationManager.NotificationReceiver().onReceive(app, Intent("action_speak_or_pause"))

        assertEquals("the damage is remembered, not re-checked per broadcast", 1, opens)
        assertFalse(CommonUtils.initialized)
        assertFalse("settings reads answer defaults instead of throwing", DatabaseContainer.ready)
    }

    @Test fun aScreenStartedDirectlyHandsOverToTheStartupRecovery() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(app, NavRoutes.READING),
        )
        try {
            controller.create()
            assertTrue(controller.get().isFinishing)
            val next = shadowOf(app).nextStartedActivity
            assertNotNull(next)
            assertEquals(StartupActivity::class.java.name, next.component?.className)
            assertTrue(next.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        } finally {
            controller.close()
        }
    }

    private companion object {
        const val BookmarkDatabaseName = "bookmarks.sqlite3"
    }
}
