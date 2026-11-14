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

import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import net.bible.test.resetComposeUiDispatcher
import net.bible.android.activity.R
import net.bible.android.activity.SpeakWidgetManager
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
    private fun damaged() = CorruptDatabasesException(listOf(CorruptDatabase(BookmarkDatabaseName, "/data/$BookmarkDatabaseName")))
    private var opens = 0
    private var wasInitialized = false
    private var wasReady = false

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasInitialized = CommonUtils.initialized
        wasReady = DatabaseContainer.ready
        CommonUtils.initialized = false
        DatabaseContainer.ready = false
        DatabaseContainer.containerFactory = { opens++; throw damaged() }
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

    /** #4112 review 2: the user learns why the widget, notification or media button did nothing. */
    @Test fun headlessEntryPostsANotificationThatOpensTheStartupRecovery() {
        AndBibleMediaButtonReceiver().onReceive(app, Intent(Intent.ACTION_MEDIA_BUTTON))

        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, notifications.size)
        val opens = shadowOf(notifications.single().contentIntent).savedIntent
        assertEquals(StartupActivity::class.java.name, opens.component?.className)
        assertEquals(app.getString(R.string.damaged_data_needs_attention), notifications.single().extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    /** #4112 review 2: a widget update renders a harmless "open the app" state instead of nothing. */
    @Test fun aSpeakWidgetUpdateRendersTheAttentionState() {
        val widgets = shadowOf(AppWidgetManager.getInstance(app))
        val id = widgets.createWidget(SpeakWidgetManager.SmallSpeakControlWidget::class.java, R.layout.speak_widget)

        val shown = widgets.getViewFor(id) as android.view.ViewGroup
        assertEquals("only the attention text, no speak buttons", 1, shown.childCount)
        assertEquals(app.getString(R.string.damaged_data_needs_attention), (shown.getChildAt(0) as android.widget.TextView).text.toString())
    }

    /**
     * #4112 review 2: a WELCOME host (restored, never through StartupActivity) that meets damaged data while leaving
     * Welcome or coming back from a welcome flow hands over and stops: no navigation, no further loads, no crash.
     */
    @Test fun aWelcomeHostThatHandsOverStopsTheFlow() {
        resetComposeUiDispatcher()
        // Built on healthy data (under isRunningTests the instance ignores `ready`, so a host cannot even be created on
        // damaged data here); the damage appears when the flow leaves Welcome, as on a restored, uninitialised host.
        DatabaseContainer.forgetReplacesForTest()
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(app, NavRoutes.WELCOME),
        )
        var detached: DatabaseContainer? = null
        try {
            controller.create().start().resume().visible()
            shadowOf(Looper.getMainLooper()).idle()
            val host = controller.get()
            val routeBefore = host.currentRouteForTest()
            assertFalse("precondition: the host has not initialised the app", host.isFinishing)
            detached = DatabaseContainer.detachInstanceForTest()
            CommonUtils.initialized = false
            DatabaseContainer.containerFactory = { opens++; throw damaged() }

            host.welcomeFlow.leaveFor(NavRoutes.download(firstDownload = true))
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("no navigation from a host that handed over", routeBefore, host.currentRouteForTest())
            assertTrue(host.isFinishing)
            assertEquals(StartupActivity::class.java.name, shadowOf(app).nextStartedActivity?.component?.className)

            host.welcomeAfterFlow()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("no navigation from a host that handed over", routeBefore, host.currentRouteForTest())
            assertEquals(StartupActivity::class.java.name, shadowOf(app).nextStartedActivity?.component?.className)
        } finally {
            controller.close()
            detached?.let { c -> kotlinx.coroutines.runBlocking { DatabaseContainer.aiLifetime.replace { c.closeAllOwned(it) } } }
        }
    }

    /** #4112 review 2: the link, share or search that opened the screen goes along to the recovery. */
    @Test fun aScreenStartedByALinkPassesTheLinkToTheStartupRecovery() {
        val link = NavHostComposeActivity.intentFor(app, NavRoutes.READING).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("https://andbible.org/bible/John.3.16")
            putExtra("openLink", "https://andbible.org/bible/John.3.16")
        }
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java, link)
        try {
            controller.create()
            val next = shadowOf(app).nextStartedActivity
            assertEquals(StartupActivity::class.java.name, next.component?.className)
            assertEquals(Intent.ACTION_VIEW, next.action)
            assertEquals(link.data, next.data)
            assertEquals("https://andbible.org/bible/John.3.16", next.getStringExtra("openLink"))
            assertTrue(next.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        } finally {
            controller.close()
        }
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
