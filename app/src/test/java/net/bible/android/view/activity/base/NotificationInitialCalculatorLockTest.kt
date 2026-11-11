package net.bible.android.view.activity.base

import android.content.Intent
import android.os.Bundle
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.device.EXTRA_FROM_NOTIFICATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Notification-marked fresh activities must apply the calculator gate at tap time. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class NotificationInitialCalculatorLockTest {
    private var originalStartupState = false

    @Before
    fun resetStartupState() {
        originalStartupState = comingFromStartupActivity
        comingFromStartupActivity = false
    }

    @After
    fun restoreStartupState() {
        comingFromStartupActivity = originalStartupState
    }

    class ProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override val allowThemeChange = false
        override val disableBaseSetupUi = true
        var receivedIntent: Intent? = null

        override fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            receivedIntent = intent
        }
    }

    @Test
    fun freshMarkedActivityLaunchesCalculatorWhenLockIsEnabledAtTapTime() = withCalculator(true) {
        val controller = Robolectric.buildActivity(ProbeActivity::class.java, notificationIntent()).setup()
        try {
            assertEquals(
                CalculatorComposeActivity::class.java.name,
                shadowOf(controller.get()).nextStartedActivityForResult?.intent?.component?.className,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun freshUnmarkedActivityDoesNotLaunchCalculatorButPauseResumeStillDoes() = withCalculator(false) {
        val controller = Robolectric.buildActivity(ProbeActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertNull(shadowOf(activity).nextStartedActivityForResult)

            controller.pause().stop()
            setCalculator(true)
            controller.start().resume()

            assertEquals(
                CalculatorComposeActivity::class.java.name,
                shadowOf(activity).nextStartedActivityForResult?.intent?.component?.className,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun recreatedMarkedActivityStillLaunchesCalculatorWhenSavedPauseStateIsFalse() = withCalculator(true) {
        val savedState = Bundle().apply {
            putBoolean("wasPaused", false)
            putBoolean("returningFromCalculator", false)
        }
        val controller = Robolectric.buildActivity(ProbeActivity::class.java, notificationIntent())
            .create(savedState).start().resume().visible()
        try {
            assertEquals(
                CalculatorComposeActivity::class.java.name,
                shadowOf(controller.get()).nextStartedActivityForResult?.intent?.component?.className,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun successfulNotificationUnlockDoesNotRelockOnConfigRecreation() = withCalculator(true) {
        val first = Robolectric.buildActivity(ProbeActivity::class.java, notificationIntent()).setup()
        val reusedIntent = first.get().intent
        try {
            assertEquals(
                CalculatorComposeActivity::class.java.name,
                shadowOf(first.get()).nextStartedActivityForResult?.intent?.component?.className,
            )
        } finally {
            first.pause().stop().destroy()
        }

        val unlockedState = Bundle().apply {
            putBoolean("wasPaused", false)
            putBoolean("returningFromCalculator", false)
        }
        val recreated = Robolectric.buildActivity(ProbeActivity::class.java, reusedIntent)
            .create(unlockedState).start().resume().visible()
        try {
            assertNull(shadowOf(recreated.get()).nextStartedActivityForResult)
        } finally {
            recreated.pause().stop().destroy()
        }
    }

    @Test
    fun foregroundNewNotificationIntentPreservesPayloadWithoutLaunchingCalculator() = withCalculator(true) {
        val controller = Robolectric.buildActivity(ProbeActivity::class.java).setup()
        try {
            val activity = controller.get()
            val notification = Intent().putExtra(EXTRA_FROM_NOTIFICATION, true).putExtra("payload", "progress")
            controller.newIntent(notification)

            assertNull(shadowOf(activity).nextStartedActivityForResult)
            assertEquals("progress", activity.receivedIntent?.getStringExtra("payload"))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun markedActivityDoesNotLaunchCalculatorWhenLockIsOff() = withCalculator(false) {
        org.junit.Assume.assumeFalse(BuildVariant.Appearance.isDiscrete)
        val controller = Robolectric.buildActivity(ProbeActivity::class.java, notificationIntent()).setup()
        try {
            assertNull(shadowOf(controller.get()).nextStartedActivityForResult)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun explicitFalseNotificationMarkerDoesNotGateAnUnlockedActivity() = withCalculator(true) {
        val intent = Intent().putExtra(EXTRA_FROM_NOTIFICATION, false)
        val controller = Robolectric.buildActivity(ProbeActivity::class.java, intent).setup()
        try {
            assertNull(shadowOf(controller.get()).nextStartedActivityForResult)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun notificationIntent() = Intent().putExtra(EXTRA_FROM_NOTIFICATION, true)

    private fun withCalculator(enabled: Boolean, block: () -> Unit) {
        val prefs = CommonUtils.realSharedPreferences
        val previous = prefs.getBoolean(SHOW_CALCULATOR_PREF, false)
        setCalculator(enabled)
        try {
            block()
        } finally {
            setCalculator(previous)
        }
    }

    private fun setCalculator(enabled: Boolean) {
        CommonUtils.realSharedPreferences.edit().putBoolean(SHOW_CALCULATOR_PREF, enabled).commit()
    }

    private companion object {
        const val SHOW_CALCULATOR_PREF = "show_calculator"
    }
}
