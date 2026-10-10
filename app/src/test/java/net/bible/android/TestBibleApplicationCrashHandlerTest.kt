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
            repeat(2) {
                application.onCreate()
                assertSame("test startup must leave the original handler installed", original,
                    Thread.getDefaultUncaughtExceptionHandler())
                application.onTerminate()
                assertSame("test applications must not stack process crash handlers", original,
                    Thread.getDefaultUncaughtExceptionHandler())
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
