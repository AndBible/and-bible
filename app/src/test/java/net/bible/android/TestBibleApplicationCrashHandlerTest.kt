package net.bible.android

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TestBibleApplicationCrashHandlerTest {
    @Test fun repeatedApplicationLifecyclesRestoreTheExactOriginalHandler() {
        val application = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            // Balance Robolectric's automatic startup before manually exercising new lifecycles.
            application.onTerminate()
            repeat(2) {
                application.onCreate()
                assertSame("test startup must leave the original handler installed", original,
                    Thread.getDefaultUncaughtExceptionHandler())
                application.onTerminate()
                val controller = org.robolectric.Robolectric.buildActivity(
                    net.bible.android.view.activity.discrete.CalculatorComposeActivity::class.java,
                ).setup()
                try {
                    org.robolectric.shadows.ShadowToast.reset()
                    net.bible.android.control.event.UserMessages.toast("terminated application probe")
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    org.junit.Assert.assertEquals("terminated lifecycles must leave no presenter subscription", 0,
                        org.robolectric.shadows.ShadowToast.shownToastCount())
                } finally {
                    controller.close()
                }
                assertSame("test applications must not stack process crash handlers", original,
                    Thread.getDefaultUncaughtExceptionHandler())
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
