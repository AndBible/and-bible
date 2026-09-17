/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.compose

import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.event.on
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Reading-host re-typing T8a item 2: **the `onResume` reconciliation `NavHostComposeActivity` did
 * not have.**
 *
 * `MainBibleActivity.onResume` (`:1964-1989`) runs a five-part block: reclaim
 * `windowControl.windowRepository` for this host, then EITHER reload the workspace (the
 * `needRefresh` arm) OR consume a pending `UpdateMainBibleActivityDocuments`, then the tilt-scroll
 * focus hand-back and `handlePendingAgentResult()`. The nav host had none of it while POSTING that
 * event from six of its own destinations — the owed-work item recorded at `ReadingNavGraph.kt:112`,
 * which names this task as its payer. Symptom: a document installed from the Download screen does
 * not appear until the workspace is reloaded.
 *
 * **What is asserted is the observable effect, not a call count.** `reloadAllWindows(true)` drives
 * `Window.loadText()`, which assigns `displayedKey` synchronously before handing the rest to
 * `Dispatchers.IO` — the same anchor
 * `ReadingDestinationInGraphTest.theComposedReadingViewsWindowsGetTheirInitialContentLoad` uses for
 * the ENTRY-time half of the same load. Nulling it and watching it come back is therefore a real
 * reload, not a proxy for one. The workspace arm is anchored on the `ToastEvent` its setter posts
 * (`ReadingCommands.currentWorkspaceId`), which is the one loud, synchronous effect of a workspace
 * switch.
 *
 * Mutations, each of which fails exactly one test below: drop the
 * `UpdateMainBibleActivityDocuments` subscription; drop the `updateDocumentsPending` gate; drop the
 * repository reclaim; swap the `else if` for a second `if` (precedence — caught by the SECOND half
 * of [theWorkspaceReloadTakesPrecedenceOverTheDocumentRefresh], not by its `ToastEvent`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostResumeReconciliationTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var toasts = 0

    @After
    fun tearDown() {
        ABEventBus.unregister(this)
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /**
     * A fully composed reading host: `.visible()` is what attaches the decor view and composes the
     * `reading` destination, and without a composed destination there is no reading view for the
     * reconciliation to reconcile.
     */
    private fun composedHost(): ActivityController<NavHostComposeActivity> =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply { create().start().resume().visible() }

    /** `Window.loadText()` assigns this synchronously; see the class kdoc. */
    private fun displayedKeyOf(window: Window): Any? =
        requireNotNull(runCatching { Window::class.java.getDeclaredField("displayedKey") }.getOrNull()) {
            "Window.displayedKey is gone — re-anchor this test on whatever loadText() now assigns"
        }.apply { isAccessible = true }.get(window)

    private fun clearDisplayedKey(window: Window) =
        Window::class.java.getDeclaredField("displayedKey")
            .apply { isAccessible = true }.set(window, null)

    private fun countToasts() {
        toasts = 0
        ABEventBus.register(this) { on<ToastEvent> { toasts++ } }
    }

    /**
     * Anti-vacuity: the entry-time load really did run and really did leave a `displayedKey`, so a
     * null one after [clearDisplayedKey] means the resume-time reload is the only thing that can
     * bring it back.
     */
    @Test
    fun theFixtureCanSeeAReload() {
        val activity = composedHost().get()
        val window = activity.hostWindowRepository.activeWindow
        assertTrue(window.isVisible, "sanity: the active window is visible, so loadText cannot return early")
        assertNotNull(displayedKeyOf(window), "the entry-time load must have run for this fixture to mean anything")
        clearDisplayedKey(window)
        assertNull(displayedKeyOf(window), "…and clearing it must actually clear it")
    }

    /**
     * The payload of the owed item: an `UpdateMainBibleActivityDocuments` that arrived while the
     * host was away is consumed on the way back.
     *
     * Before T8a nothing on this host subscribed to that event at all, so the resume did nothing
     * and `displayedKey` stayed null.
     */
    @Test
    fun aPendingDocumentUpdateReloadsTheWindowsOnResume() {
        val controller = composedHost()
        val activity = controller.get()
        val window = activity.hostWindowRepository.activeWindow
        clearDisplayedKey(window)

        controller.pause()
        ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        controller.resume()

        assertNotNull(
            displayedKeyOf(window),
            "a document update that arrived while this host was away must be consumed on resume — " +
                "otherwise a document installed from the Download screen does not appear until the " +
                "workspace is reloaded",
        )
    }

    /**
     * The gate half. Without the `updateDocumentsPending` flag a reload on EVERY resume would pass
     * the test above while being a different thing entirely: classic reloads only when something
     * asked it to.
     */
    @Test
    fun aResumeWithNoPendingUpdateReloadsNothing() {
        val controller = composedHost()
        val activity = controller.get()
        val window = activity.hostWindowRepository.activeWindow
        clearDisplayedKey(window)

        controller.pause()
        controller.resume()

        assertNull(
            displayedKeyOf(window),
            "an ordinary resume must not reload every window — the refresh is gated on a pending " +
                "UpdateMainBibleActivityDocuments, exactly as classic's is",
        )
    }

    /**
     * Classic's `needRefresh` arm: `windowControl.windowRepository` is whichever reading host
     * resumed last, so a host coming back to the front reclaims it. R6c1/R6d's identity finding is
     * why this matters at all — with two live hosts the collaborators that read `windowControl`
     * would otherwise still be pointed at the other host's workspace.
     */
    @Test
    fun aResumingHostReclaimsWindowControlForItsOwnRepository() {
        val controller = composedHost()
        val activity = controller.get()
        val foreign = WindowRepository(activity.lifecycleScope)

        controller.pause()
        CommonUtils.windowControl.windowRepository = foreign
        controller.resume()

        assertSame(
            activity.hostWindowRepository, CommonUtils.windowControl.windowRepository,
            "a resumed reading host must publish ITS OWN repository — every collaborator that " +
                "reads windowControl.windowRepository is otherwise looking at another workspace",
        )
    }

    /**
     * The PRECEDENCE classic encodes with `else if`, and the reason R8 refused to port one line of
     * the block: a host that refreshes documents but never reconciles the workspace is worse than
     * one that honestly does neither. When the repository has to be reclaimed, the whole workspace
     * is reloaded and the document refresh is not ALSO run.
     *
     * **The second half is what makes this see the mutation** (fix round 1, review Minor 2a). The
     * `ToastEvent` assertion alone cannot: with a second `if` instead of `else if` BOTH arms run and
     * a toast is still posted. What only `else if` produces is a pending flag that SURVIVES the
     * workspace arm — `updateDocuments()` is the only thing that clears it, and classic does not
     * clear it on the needRefresh path — so the next ordinary resume is the one that performs the
     * document refresh. With the mutation the first resume consumes the flag and the second reloads
     * nothing.
     *
     * The window is re-read AFTER the workspace reload on purpose: `loadFromDb` rebuilds the
     * repository's `Window` objects, so the one captured before it is no longer the active window
     * and asserting on it would prove nothing either way.
     */
    @Test
    fun theWorkspaceReloadTakesPrecedenceOverTheDocumentRefresh() {
        val controller = composedHost()
        val activity = controller.get()
        val foreign = WindowRepository(activity.lifecycleScope)

        controller.pause()
        CommonUtils.windowControl.windowRepository = foreign
        ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        countToasts()
        controller.resume()

        assertTrue(
            toasts > 0,
            "the needRefresh arm must run the full workspace reload (whose setter posts a " +
                "ToastEvent naming the workspace), not the document refresh",
        )

        val window = activity.hostWindowRepository.activeWindow
        assertTrue(window.isVisible, "sanity: the reloaded workspace's active window is visible")
        clearDisplayedKey(window)

        controller.pause()
        controller.resume()

        assertNotNull(
            displayedKeyOf(window),
            "the pending document update must SURVIVE the workspace arm and be consumed by the " +
                "next resume — only `else if` does that, because updateDocuments() is the one " +
                "thing that clears the flag. A second `if` runs both arms, consumes it on the " +
                "first resume, and leaves this one with nothing to do",
        )
    }

    /** The other side of the precedence pair: an ordinary refresh must NOT reload the workspace. */
    @Test
    fun aPlainDocumentRefreshDoesNotReloadTheWorkspace() {
        val controller = composedHost()
        val activity = controller.get()
        clearDisplayedKey(activity.hostWindowRepository.activeWindow)

        controller.pause()
        ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        countToasts()
        controller.resume()

        assertNotNull(
            displayedKeyOf(activity.hostWindowRepository.activeWindow),
            "sanity: this is the document-refresh arm",
        )
        assertTrue(
            toasts == 0,
            "a pending document update must not switch the workspace — that is the other arm, and " +
                "reloading the workspace on every document install would throw away scroll " +
                "position and window state",
        )
    }
}
