package net.bible.android.view.activity.page

import android.widget.FrameLayout
import android.webkit.RenderProcessGoneDetail
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewRendererRecoveryTest {
    private lateinit var repository: WindowRepository
    private lateinit var activity: NavHostComposeActivity
    private lateinit var readingHost: ComposeReadingViewHost
    private lateinit var window: Window
    private lateinit var factory: BibleViewFactory
    private lateinit var parent: FrameLayout

    @Before fun setup() {
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        // A real reading-host graph, never .create()d: no Compose rendering or first-run UI.
        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = repository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)
        readingHost = net.bible.test.fixtureReadingHost(activity)
        activity.composeReadingViewHost = readingHost
        window = repository.activeWindow
        factory = activity.readingCommands.bibleViewFactory
        parent = FrameLayout(activity)
    }

    @After fun cleanup() {
        if (::parent.isInitialized) parent.removeAllViews()
        if (::factory.isInitialized) factory.clear()
        if (::activity.isInitialized) {
            activity.lifecycleScope.cancel()
            CurrentActivityHolder.deactivate(activity)
        }
        if (::repository.isInitialized) DatabaseResetter.resetDatabase(repository.scope)
    }

    @Test fun rendererDeathDisposesTheCachedViewAndReloadsAfterComposeRemount() {
        val dead = factory.getOrCreateBibleView(window)
        parent.addView(dead)
        val generationBefore = readingHost.generationForTest.state.value
        val loadBefore = window.contentLoadGeneration
        val removeFromCache = dead.onDestroy
        dead.onDestroy = {
            assertNull("Detach before native destroy", dead.parent)
            removeFromCache?.invoke()
        }

        assertTrue(dead.webViewClient.onRenderProcessGone(dead, mock(RenderProcessGoneDetail::class.java)))

        assertNull("A dead WebView must leave the hierarchy", dead.parent)
        assertTrue("Destroy the native WebView, not only its bridge", shadowOf(dead).wasDestroyCalled())
        assertTrue(dead.session.isDisposed)
        assertNull(window.bibleView)
        assertEquals(generationBefore + 1, readingHost.generationForTest.state.value)
        assertEquals("Reload the same window's document", loadBefore + 1, window.contentLoadGeneration)

        // Simulate the AndroidView factory re-running after the generation changes.
        val replacement = factory.getOrCreateBibleView(window)
        parent.addView(replacement)
        assertNotSame("The factory must not reuse the dead cached view", dead, replacement)
        assertSame(replacement, window.bibleView)
        assertFalse(shadowOf(replacement).wasDestroyCalled())
        assertSame(replacement, factory.getOrCreateBibleView(window))
    }

    @Test fun rendererDeathWithoutAMountedHostStillDisposesTheCachedView() {
        activity.composeReadingViewHost = null
        val dead = factory.getOrCreateBibleView(window)
        parent.addView(dead)

        assertTrue(dead.webViewClient.onRenderProcessGone(dead, mock(RenderProcessGoneDetail::class.java)))

        assertNull(dead.parent)
        assertTrue(shadowOf(dead).wasDestroyCalled())
        assertNull(window.bibleView)
        assertNotSame(dead, factory.getOrCreateBibleView(window))
    }
}
