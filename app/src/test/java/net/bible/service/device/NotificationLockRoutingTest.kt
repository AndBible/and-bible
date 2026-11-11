package net.bible.service.device

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.StartupActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.installzip.DocumentInstallService
import net.bible.service.llm.agent.AgentForegroundService
import net.bible.service.sword.index.SearchIndexingService
import net.bible.sharedcore.nav.NavRoutes
import org.crosswire.common.progress.JobManager
import org.crosswire.jsword.book.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Notification content must not create a fresh, never-paused reading host behind the PIN lock. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class NotificationLockRoutingTest {
    private val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()

    @Test fun lockedNotificationEntryPointsUseStartupInsteadOfReading() = withCalculator(true) {
        val intents = notificationContentIntents()
        assertEquals(
            intents.map { (source, _) -> source to StartupActivity::class.java.name },
            intents.map { (source, intent) -> source to intent.component?.className },
        )
        intents.forEach { (source, intent) ->
            // Reusing a StartupActivity already awaiting a calculator result would skip a fresh check.
            assertEquals(source, 0, intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP)
            assertFalse(source, intent.hasExtra(NavHostComposeActivity.EXTRA_ROUTE))
        }
    }

    @Test fun standardNotificationEntryPointsKeepTheirDestinations() = withCalculator(false) {
        notificationContentIntents().forEach { (source, intent) ->
            assertEquals(source, NavHostComposeActivity::class.java.name, intent.component?.className)
            assertEquals(source, if (source == "progress") NavRoutes.PROGRESS_STATUS_PATTERN else NavRoutes.READING,
                intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        }
    }

    private fun notificationContentIntents(): List<Pair<String, Intent>> {
        val install = Robolectric.buildService(DocumentInstallService::class.java).get()
        val agent = Robolectric.buildService(AgentForegroundService::class.java).get()
        val indexing = Robolectric.buildService(SearchIndexingService::class.java).get()
        val indexNotification = SearchIndexingService::class.java
            .getDeclaredMethod("notification", Book::class.java, Int::class.javaObjectType)
            .apply { isAccessible = true }.invoke(indexing, null, null) as Notification
        val intents = listOf(
            "indexing" to snapshot(indexNotification.contentIntent),
            "install (progress and decision)" to snapshot(mainIntent(install)),
            "agent (running, progress, permission and completion)" to snapshot(mainIntent(agent)),
        )
        val progress = ProgressNotificationManager()
        val job = JobManager.createJob("INSTALL_BOOK-PIN-TEST", "Installing test book", null)
        progress.initialise()
        try {
            job.beginJob("install", 100)
            job.work = 10
            val manager = app.getSystemService(android.app.NotificationManager::class.java)
            val progressNotification = shadowOf(manager).allNotifications.single { it.contentIntent != null }
            assertNotNull(progressNotification.contentIntent)
            return intents + ("progress" to snapshot(progressNotification.contentIntent))
        } finally {
            job.done()
            progress.close()
        }
    }

    private fun snapshot(pending: PendingIntent): Intent = Intent(shadowOf(pending).savedIntent).also {
        // These producers historically share requestCode/component; isolate each producer's intent.
        pending.cancel()
    }

    private fun mainIntent(service: Service): PendingIntent = service.javaClass
        .getDeclaredMethod("buildMainActivityIntent").apply { isAccessible = true }
        .invoke(service) as PendingIntent

    private fun withCalculator(enabled: Boolean, block: () -> Unit) {
        val prefs = CommonUtils.realSharedPreferences
        val previous = prefs.getBoolean("show_calculator", false)
        prefs.edit().putBoolean("show_calculator", enabled).commit()
        try {
            block()
        } finally {
            prefs.edit().putBoolean("show_calculator", previous).commit()
        }
    }
}
