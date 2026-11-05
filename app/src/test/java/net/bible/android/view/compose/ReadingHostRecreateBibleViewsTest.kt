package net.bible.android.view.compose

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.ReadingFixtureOwners
import net.bible.test.awaitReadingGraph
import net.bible.test.closeReadingHostsBeforeReset
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/** #3976: the factory belongs to the Activity, not to a retained window or a Compose destination. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostRecreateBibleViewsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private val factories = mutableListOf<BibleViewFactory>()

    @After fun cleanup() {
        // Also clean up on the unfixed code, where Activity destruction leaks these views.
        closeReadingHostsBeforeReset(controllers, afterClose = { factories.forEach { it.clear() } })
    }

    private fun host(): ActivityController<NavHostComposeActivity> {
        resetComposeUiDispatcher()
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().also {
            awaitReadingGraph(it.get()) { compose.waitForIdle() }
            factories += it.get().readingCommands.bibleViewFactory
        }
    }

    @Test fun recreatedHostDestroysEveryOldViewAndStopsItsBridgeEvents() {
        val controller = host()
        val oldHost = controller.get()
        val repo = oldHost.hostWindowRepository
        val factory = oldHost.readingCommands.bibleViewFactory
        val windows = listOf(repo.activeWindow, repo.addNewWindow(repo.activeWindow))
        val oldViews = windows.map { factory.getOrCreateBibleView(it) }
        val oldTags = windows.map { "BibleView[${it.displayId}]" }.toSet()
        val events = GlobalContext.get().get<WindowStateServiceImpl>()
        fun sendBridgeEvents() {
            events.notify(WindowChange.ActiveWindowChanged(windows.first()))
            WorkspaceChanges.notifySettingsEdited()
        }
        fun bridgeCalls() = ShadowLog.getLogs().filter {
            it.tag in oldTags && it.msg.contains("HTML not yet ready")
        }

        // Robolectric does not boot the JS page. The real channel logs each delivery while loading,
        // making both event paths observable without mocking the view, relay or bridge.
        ShadowLog.clear()
        sendBridgeEvents()
        oldTags.forEach { tag ->
            assertTrue("fixture: $tag receives active-window events", bridgeCalls().any {
                it.tag == tag && it.msg.contains("set_active")
            })
            assertTrue("fixture: $tag receives workspace configuration events", bridgeCalls().any {
                it.tag == tag && it.msg.contains("set_config")
            })
        }

        ReadingFixtureOwners.retain(oldHost)
        resetComposeUiDispatcher()
        controller.recreate()
        val replacement = controller.get()
        assertNotSame(oldHost, replacement)
        assertTrue("cross the actual configuration-recreation boundary", oldHost.isChangingConfigurations)
        assertNotSame(factory, replacement.readingCommands.bibleViewFactory)
        factories += replacement.readingCommands.bibleViewFactory

        // Check delivery before checking disposal: a live subscription is the reported failure,
        // not merely a missing WebView.destroy() call.
        // The recreated window may have the same display id, so inspect each old native view,
        // not its log tag. Flush detached-view queues explicitly; no JS page runs in Robolectric.
        oldViews.forEach {
            it.jsChannel.markClientReady()
            it.jsChannel.execute(JsCall("oldViewDeliverySentinel()"))
            it.jsChannel.flush()
            assertEquals("oldViewDeliverySentinel();", shadowOf(it).lastEvaluatedJavascript)
        }
        val oldScripts = oldViews.map { shadowOf(it).lastEvaluatedJavascript }
        sendBridgeEvents()
        oldViews.forEach { it.jsChannel.flush() }
        assertEquals("no old view may receive bridge events after recreation", oldScripts,
            oldViews.map { shadowOf(it).lastEvaluatedJavascript })
        oldViews.forEach { view ->
            assertFalse(view.listenEvents)
            assertTrue("destroy the native WebView", shadowOf(view).wasDestroyCalled())
            assertTrue("cancel queued session work", view.session.isDisposed)
            assertNull("detach before native destruction", view.parent)
        }
        windows.forEach { assertNull("the old window must not retain its view", it.bibleView) }

        awaitReadingGraph(replacement) { compose.waitForIdle() }
        val newWindow = replacement.hostWindowRepository.activeWindow
        val newView = replacement.readingCommands.bibleViewFactory.getOrCreateBibleView(newWindow)
        assertTrue(newView.listenEvents)
        assertFalse(shadowOf(newView).wasDestroyCalled())
        assertSame(newView, newWindow.bibleView)
        assertTrue("the new factory never adopts the old Activity's views", oldViews.none { it === newView })
    }

    @Test fun hostDestructionAlsoDestroysViewsParkedForWorkspaceReuse() {
        val controller = host()
        val activity = controller.get()
        val repo = activity.hostWindowRepository
        val factory = activity.readingCommands.bibleViewFactory
        val views = listOf(repo.activeWindow, repo.addNewWindow(repo.activeWindow))
            .map { factory.getOrCreateBibleView(it) }
        factory.retireForReuse()
        assertTrue("parked views stay alive until their host dies or adopts them", views.none { shadowOf(it).wasDestroyCalled() })

        ReadingFixtureOwners.retain(activity)
        controller.pause().stop().destroy()
        controllers.remove(controller)

        views.forEach {
            assertTrue("an unadopted spare must not leak its Activity", shadowOf(it).wasDestroyCalled())
            assertTrue(it.session.isDisposed)
            assertFalse(it.listenEvents)
        }
    }
}
