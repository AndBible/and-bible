package net.bible.android.view.compose

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.cloudsync.SyncTriggerDriver
import net.bible.test.closeReadingHostsBeforeReset
import net.bible.test.resetComposeUiDispatcher
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.FixMethodOrder
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Guard for the sync-timer test leak: a reading host reports the app foreground, which starts the process-wide
 * [SyncTriggerDriver] timer on `Dispatchers.IO`. The fixture teardown must stop it, otherwise it keeps reading settings
 * after the test's Application and database are gone and crashes a later test with an uncaught NPE.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncTimerLeakGuardTest {
    @Before fun pinFirstTime() { net.bible.android.view.activity.base.firstTime = false }

    @Test
    fun a_readingHostStartsTheTimerAndTheStandardFixtureClosesIt() {
        resetComposeUiDispatcher()
        val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply { create().start().resume().visible() }
        val driver = controller.get().readingAppBootstrap.syncTriggers
        assertNotNull(driver)
        leaked = driver
        closeReadingHostsBeforeReset(controllers)
    }

    companion object {
        var leaked: SyncTriggerDriver? = null

        @AfterClass @JvmStatic fun timerDoesNotOutliveTheFixture() {
            val d = leaked ?: return
            assertFalse(d.timerRunning, "sync timer outlived the fixture teardown")
            assertFalse(d.isForeground, "driver still in foreground after fixture teardown")
        }
    }
}
