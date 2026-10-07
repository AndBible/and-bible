package net.bible.android.control.event

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.AppPosition
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ABEventBus phase 8, Task 1: the lifecycle owner streams fire from their real posters. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LifecycleOwnerStreamsTest {
    private fun <T : Any> record(events: Events<T>): Pair<MutableList<T>, Subscription> {
        val got = mutableListOf<T>()
        return got to events.subscribe { got += it }
    }

    @Test fun hideStatusBarNotifiesAndOtherKeysDoNot() {
        val (got, sub) = record(SystemBarSettingChanges.changes)
        try {
            val service = AppSettingsServiceImpl()
            service.setBool("hide_status_bar", true)
            service.setBool("monochrome_mode", true)
            assertEquals(1, got.size)
        } finally {
            sub.cancel()
            CommonUtils.settings.removeBoolean("hide_status_bar")
            CommonUtils.settings.removeBoolean("monochrome_mode")
            CommonUtils.realSharedPreferences.edit().remove("hide_status_bar").remove("monochrome_mode").apply()
        }
    }

    @Test fun firstActivationIsForegroundAndLastDeactivationIsBackground() {
        val (got, sub) = record(CurrentActivityHolder.appPositionChanges)
        // ActivityBase.onCreate activates itself, so creating is what activates.
        val a = Robolectric.buildActivity(CalculatorComposeActivity::class.java).create().get()
        val b = Robolectric.buildActivity(CalculatorComposeActivity::class.java).create().get()
        try {
            CurrentActivityHolder.deactivate(b); CurrentActivityHolder.deactivate(a)
            assertEquals(listOf(AppPosition.FOREGROUND, AppPosition.BACKGROUND), got)
        } finally {
            sub.cancel()
            CurrentActivityHolder.deactivate(b); CurrentActivityHolder.deactivate(a)
        }
    }

    @Test fun lightSensorFlipNotifiesNightMode() {
        val (got, sub) = record(ScreenSettings.nightModeChanges)
        try { ScreenSettings.notifyNightModeChanged(); assertEquals(1, got.size) } finally { sub.cancel() }
    }
}
