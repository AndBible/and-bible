package net.bible.android.view.compose

import android.Manifest
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.TestBibleApplication
import net.bible.android.control.activity.ActivityMenuPreference
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.android.platform.AndroidSpeakPlatform
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.ActivityMenuDefault
import net.bible.sharedcore.platform.AppSettings
import net.bible.test.closeReadingHostsBeforeReset
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class DiscreteSpeakRegressionTest {
    private val hosts = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var oldFirstTime = false
    private var discrete: Boolean? = null
    private var calculator: Boolean? = null
    private var menu: Boolean? = null
    private var lastUsed: Long? = null

    @Before fun setUp() {
        oldFirstTime = firstTime
        firstTime = false
        val prefs = CommonUtils.realSharedPreferences
        discrete = if (prefs.contains("discrete_mode")) prefs.getBoolean("discrete_mode", false) else null
        calculator = if (prefs.contains("show_calculator")) prefs.getBoolean("show_calculator", false) else null
        menu = CommonUtils.settings.getBoolean(ActivityMenuPreference.KEY, false).let {
            val a = CommonUtils.settings.getBoolean(ActivityMenuPreference.KEY, true)
            if (it == a) it else null
        }
        lastUsed = CommonUtils.settings.getLong("speak-last-used", Long.MIN_VALUE).let { if (it == Long.MIN_VALUE) null else it }
    }

    @After fun tearDown() {
        try {
            closeReadingHostsBeforeReset(hosts, restoreBeforeReset = {
                val edit = CommonUtils.realSharedPreferences.edit()
                if (discrete == null) edit.remove("discrete_mode") else edit.putBoolean("discrete_mode", discrete!!)
                if (calculator == null) edit.remove("show_calculator") else edit.putBoolean("show_calculator", calculator!!)
                edit.commit()
                if (menu == null) CommonUtils.settings.removeBoolean(ActivityMenuPreference.KEY)
                else CommonUtils.settings.setBoolean(ActivityMenuPreference.KEY, menu!!)
                if (lastUsed == null) CommonUtils.settings.removeLong("speak-last-used")
                else CommonUtils.settings.setLong("speak-last-used", lastUsed!!)
                get<ActivityMenuPreference>(ActivityMenuPreference::class.java).refresh()
            })
        } finally {
            firstTime = oldFirstTime
        }
    }

    private fun setDiscrete(value: Boolean) {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", value).commit()
    }

    private fun host(): ActivityController<NavHostComposeActivity> {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { hosts += it }
        controller.create().start().resume().visible()
        net.bible.test.awaitReadingGraph(controller.get())
        return controller
    }

    @Test fun preparingDiscreteSpeechDoesNotAskPermissionAndKeepsAudioSetup() {
        assertPreparationWithoutPermission(discreteMode = true)
    }

    @Test fun preparingStandardSpeechDoesNotAskPermissionAndKeepsAudioSetup() {
        assertPreparationWithoutPermission(discreteMode = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun assertPreparationWithoutPermission(discreteMode: Boolean) {
        setDiscrete(discreteMode)
        val activity = host().get()
        val shadow = shadowOf(activity)
        shadow.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val before = System.currentTimeMillis()
            AndroidSpeakPlatform(activity.applicationContext).onPrepareForSpeaking()
            // Run pending main-looper work so a (regressed) permission request would surface now.
            shadowOf(Looper.getMainLooper()).idle()
            assertNull("speech must not request notification permission", shadow.lastRequestedPermission)
            assertTrue(CommonUtils.settings.getLong("speak-last-used", 0) >= before)
            assertEquals(AudioManager.STREAM_MUSIC, activity.volumeControlStream)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun returningToReadingLeavesNoPauseExemptionSoRealBackgroundLocks() {
        setDiscrete(true)
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", true).commit()
        val controller = host()
        val activity = controller.get()
        AndroidSpeakPlatform(activity.applicationContext).bringReadingToFront()
        // Guard: the in-graph host launch leaves no expected-pause flag (a plain activity.startActivity
        // would), so a real background leave below still relocks behind the calculator.
        assertNull("in-graph self-launch must not trigger calculator", shadowOf(activity).nextStartedActivityForResult)
        controller.pause().stop().start().resume()
        assertEquals(CalculatorComposeActivity::class.java.name, shadowOf(activity).nextStartedActivityForResult?.intent?.component?.className)
    }

    @Test fun internalStartActivityLeavesNoPauseExemptionSoHomeStillLocks() {
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", true).commit()
        val controller = host()
        val activity = controller.get()
        activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))
        assertNull("in-graph navigation must not launch a platform activity", shadowOf(activity).nextStartedActivityForResult)
        controller.pause().stop().start().resume()
        assertEquals(CalculatorComposeActivity::class.java.name, shadowOf(activity).nextStartedActivityForResult?.intent?.component?.className)
    }

    @Test fun stoppingTwiceHidesAndBothEntryPointsReopenAndNotifyInsets() {
        val activity = host().get()
        val service = get<SpeakTransportServiceImpl>(SpeakTransportServiceImpl::class.java)
        val commands = activity.readingCommands
        commands.composeShowSpeakTransport()
        assertTrue(service.state.value.visible)
        val events = mutableListOf<Unit>()
        val subscription = activity.readingInsets.offsetsChanged.subscribe { events += Unit }
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            events.clear()
            service.stop()
            service.stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertFalse(service.state.value.visible)
            val selfHideNotifiedInsets = events.isNotEmpty()
            commands.composeShowSpeakTransport()
            assertTrue("menu must reopen", service.state.value.visible)
            service.stop()
            service.stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertFalse(service.state.value.visible)
            commands.composeToggleSpeak()
            assertTrue("toolbar must reopen", service.state.value.visible)
            assertTrue("self-hide must notify insets", selfHideNotifiedInsets)
        } finally {
            subscription.cancel()
        }
    }

    @Test fun activityMenuDefaultTracksDiscreteAndExplicitChoiceWinsInSettings() {
        val settings = get<AppSettings>(AppSettings::class.java)
        val default = get<ActivityMenuDefault>(ActivityMenuDefault::class.java)
        for (discreteMode in listOf(false, true)) {
            setDiscrete(discreteMode)
            settings.removeBoolean(ActivityMenuPreference.KEY)
            val preference = ActivityMenuPreference(settings, default)
            val service = AppSettingsServiceImpl(activityMenu = preference)
            // The discrete flavor is discrete regardless of the preference, so its default is always true.
            val expectedDefault = discreteMode || net.bible.service.common.BuildVariant.Appearance.isDiscrete
            assertEquals(expectedDefault, preference.enabled.value)
            assertEquals(expectedDefault, service.snapshot.value.showActivityInMenu)
            service.setBool(ActivityMenuPreference.KEY, !expectedDefault)
            assertEquals(!expectedDefault, preference.enabled.value)
            assertEquals(!expectedDefault, service.snapshot.value.showActivityInMenu)
            assertEquals(!expectedDefault, ActivityMenuPreference(settings, default).enabled.value)
        }
    }
}
