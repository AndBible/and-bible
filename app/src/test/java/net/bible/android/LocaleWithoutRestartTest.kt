package net.bible.android

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import net.bible.android.activity.SpeakWidgetManager
import net.bible.service.cloudsync.SyncService
import org.junit.Assert.assertNotEquals
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.content.res.Configuration
import android.os.LocaleList
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Fix batch 5 §1.2 (F112): the Application follows `locale_pref` live, no process restart.
 * `R.string.error_occurred` (not `okay`, which is "OK" in both languages -- C4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK], qualifiers = "en")
class LocaleWithoutRestartTest {
    private val app get() = BibleApplication.application
    private fun setPref(v: String?) = CommonUtils.realSharedPreferences.edit().apply {
        if (v == null) remove("locale_pref") else putString("locale_pref", v)
    }.commit()

    @After fun reset() { setPref(null) }

    @Test fun changingThePreferenceUpdatesTheApplicationStrings() {
        setPref("fi")
        assertEquals("Tapahtui virhe", app.getString(R.string.error_occurred))
    }

    @Test fun removingThePreferenceRestoresTheSystemLanguage() {
        setPref("fi")
        assertEquals("fi", Locale.getDefault().language)
        setPref(null)
        assertEquals("An error has occurred", app.getString(R.string.error_occurred))
        assertEquals("en", Locale.getDefault().language)
    }

    @Test fun notificationChannelsAreRenamed() {
        setPref("fi")
        val nm = app.getSystemService(NotificationManager::class.java)
        assertEquals("Virheiden notifikaatiot", nm.getNotificationChannel("generic-notifications")?.name?.toString())
    }

    @Test fun aConfigurationChangeKeepsTheChosenLanguage() {
        setPref("fi")
        RuntimeEnvironment.setQualifiers("+land")
        // Robolectric does not dispatch it; ActivityThread does on a real device.
        app.onConfigurationChanged(app.baseContext.resources.configuration)
        assertEquals("Tapahtui virhe", app.getString(R.string.error_occurred))
    }

    /** Review fix: "Default" must not narrow an Activity's locale LIST to the first system locale. */
    @Test fun defaultKeepsTheWholeLocaleListOfTheBaseContext() {
        setPref(null)
        val twoLanguages = Configuration(app.baseContext.resources.configuration)
        twoLanguages.setLocales(LocaleList(Locale.forLanguageTag("gsw"), Locale.GERMAN))
        val base = app.baseContext.createConfigurationContext(twoLanguages)
        val localized = LocaleHelper.localized(base)
        val locales = localized.resources.configuration.locales
        // Robolectric may reorder the list, so compare as a set: both languages must survive.
        assertEquals(setOf("gsw", "de"), (0 until locales.size()).map { locales[it].language }.toSet())
    }

    /** The notification SyncService.synchronize() posts, built without starting the IO coroutine (no race). */
    private fun syncNotificationTitle(): String? {
        val service = Robolectric.setupService(SyncService::class.java)
        return service.buildSyncNotification().extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    }

    /** F114: a Service's own Resources do not follow `locale_pref`; the Application's do. */
    @Test fun aServiceNotificationFollowsTheAppLanguage() {
        setPref("fi")
        val title = syncNotificationTitle()
        assertEquals(app.getString(R.string.synchronizing), title)
        assertNotEquals("Synchronizing\u2026", title)
    }

    /** Review Focus 4. */
    @Test fun defaultLanguageGivesTheSystemLanguageText() {
        setPref("fi"); setPref("")
        // A literal, not app.getString: were the Application still Finnish both sides would match.
        assertEquals("Synchronizing\u2026", syncNotificationTitle())
    }

    /** `app_name_medium` is translated in `ar` (not in `fi`), so use `ar` to tell the languages apart. */
    @Test fun theSpeakWidgetTitleFollowsALanguageChange() {
        val existing = SpeakWidgetManager.instance
        val mgr = existing ?: SpeakWidgetManager()
        try {
            val english = app.getString(R.string.app_name_medium)
            assertEquals(english, mgr.titleForTest())
            setPref("ar")
            val arabic = app.getString(R.string.app_name_medium)
            assertNotEquals(english, arabic)
            assertEquals(arabic, mgr.titleForTest())
            setPref("")
            assertEquals(english, mgr.titleForTest())
        } finally { if (existing == null) mgr.destroy() } // only what this test created
    }
}
