package net.bible.android.view.compose

import android.app.Activity
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Spec §3.3: BACK on the calculator disguise finishes it with RESULT_CANCELED, through the dispatcher. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CalculatorBackTest {
    @Test fun backFinishesTheCalculatorAsCancelled() {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            val a = controller.get()
            a.onBackPressedDispatcher.onBackPressed()
            assertTrue(a.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
        } finally {
            runCatching { controller.pause().stop().destroy() }
        }
    }
}
