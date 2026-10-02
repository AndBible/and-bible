package net.bible.android

import android.app.NotificationManager
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
}
