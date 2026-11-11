package net.bible.android.view.activity.base

import android.app.Activity
import android.content.Intent
import android.net.Uri
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** External activity results cannot prove that the user did not visit Home while away. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class ExternalActivityCalculatorLockTest {
    private var originalStartupState = false
    private var originalCalculator: Boolean? = null

    @Before
    fun enableCalculator() {
        originalStartupState = comingFromStartupActivity
        comingFromStartupActivity = false
        val prefs = CommonUtils.realSharedPreferences
        originalCalculator = if (prefs.contains("show_calculator")) prefs.getBoolean("show_calculator", false) else null
        prefs.edit().putBoolean("show_calculator", true).commit()
    }

    @After
    fun restoreGlobals() {
        comingFromStartupActivity = originalStartupState
        CommonUtils.realSharedPreferences.edit().apply {
            originalCalculator?.let { putBoolean("show_calculator", it) } ?: remove("show_calculator")
        }.commit()
    }

    class ProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override val allowThemeChange = false
        override val disableBaseSetupUi = true
    }

    @Test
    fun browserThenHomeThenLauncherReturnRequiresCalculator() = withActivity { controller ->
        val activity = controller.get()
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        shadowOf(activity).nextStartedActivityForResult
        // Home happens in the browser: our host gets no additional pause callback.
        controller.pause().stop().restart().start().resume()
        assertCalculator(activity)
    }

    @Test
    fun externalPickerResultStillRequiresCalculatorFailSafe() = withActivity { controller ->
        val activity = controller.get()
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
        activity.startActivityForResult(picker, 42)
        shadowOf(activity).nextStartedActivityForResult
        controller.pause().stop()
        shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.parse("content://backup/1")))
        controller.restart().start().resume()
        assertCalculator(activity)
    }

    @Test
    fun internalActivityReturnDoesNotRequireCalculator() = withActivity { controller ->
        val activity = controller.get()
        activity.startActivityForResult(Intent(activity, ProbeActivity::class.java), 42)
        shadowOf(activity).nextStartedActivityForResult
        controller.pause().stop().restart().start().resume()
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test
    fun configurationChangeDoesNotRequireCalculator() = withActivity { controller ->
        controller.recreate()
        assertNull(shadowOf(controller.get()).nextStartedActivityForResult)
    }

    @Test
    fun externalActivityReturnDoesNotLockWhenCalculatorIsDisabled() = withActivity { controller ->
        org.junit.Assume.assumeFalse(BuildVariant.Appearance.isDiscrete)
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", false).commit()
        val activity = controller.get()
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        shadowOf(activity).nextStartedActivityForResult
        controller.pause().stop().restart().start().resume()
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    private fun assertCalculator(activity: Activity) {
        assertEquals(
            CalculatorComposeActivity::class.java.name,
            shadowOf(activity).nextStartedActivityForResult?.intent?.component?.className,
        )
    }

    private fun withActivity(block: (org.robolectric.android.controller.ActivityController<ProbeActivity>) -> Unit) {
        val controller = Robolectric.buildActivity(ProbeActivity::class.java).setup()
        try {
            block(controller)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
