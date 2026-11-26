package net.bible.android.view.compose

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.page.screen.PaneMenuAnchor
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Reading menus hide the IME via the window controller, not focused-view lookup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = TestBibleApplication::class, shadows = [RecordingWindowInsetsControllerShadow::class])
class ReadingMenusHideKeyboardTest {
    private lateinit var repository: WindowRepository
    private lateinit var host: NavHostComposeActivity
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private lateinit var reading: ComposeReadingViewHost
    private lateinit var container: ActivityController<Activity>

    @Before fun setup() {
        RecordingWindowInsetsControllerShadow.hiddenTypes = 0
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        DatabaseResetter.runResetBlocking {
            GlobalContext.get().get<net.bible.service.cloudsync.documents.DocumentSyncSettings>().ensureLoaded()
        }
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        controllers.add(controller)
        host = controller.get()
        host.readingAppBootstrap.windowRepository = repository
        host.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(host)
        reading = net.bible.test.fixtureReadingHost(host)
        host.composeReadingViewHost = reading
        container = Robolectric.buildActivity(Activity::class.java).setup()
        container.get().setContentView(host.window.decorView)
        shadowOf(host).setCurrentFocus(null)
        assertTrue("host has no focused view", host.currentFocus == null)
        assertTrue("decor remains attached", host.window.decorView.windowToken != null)
    }

    @After fun cleanup() {
        (container.get().findViewById<View>(android.R.id.content) as ViewGroup).removeAllViews()
        container.pause().stop().destroy()
        net.bible.test.closeReadingHostsBeforeReset(
            controllers,
            afterClose = { CurrentActivityHolder.deactivate(host) },
            reset = { DatabaseResetter.resetDatabase(repository.scope) },
        )
    }

    private fun assertImeHidden() = assertEquals(WindowInsetsCompat.Type.ime(), RecordingWindowInsetsControllerShadow.hiddenTypes)

    @Test fun openingPaneMenuHidesImeWithoutFocusedView() {
        reading.hostState.openPaneMenu(repository.activeWindow.id.toString(), PaneMenuAnchor.Pane)
        assertNotNull("the pane menu opens", reading.hostState.paneMenuWindowId.value)
        assertImeHidden()
    }

    @Test fun openingOverflowMenuHidesImeWithoutFocusedView() {
        reading.openOverflowMenu()
        assertTrue("the overflow menu opens", reading.overflowExpandedForTest)
        assertImeHidden()
    }
}

@Implements(className = "android.view.InsetsController", minSdk = 30, isInAndroidSdk = false)
class RecordingWindowInsetsControllerShadow {
    companion object { var hiddenTypes: Int = 0 }

    @Implementation fun hide(types: Int) {
        hiddenTypes = hiddenTypes or types
    }
}
