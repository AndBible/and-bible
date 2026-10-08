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

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.control.document.DocumentChanges
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F57. Installing a document and returning to the reading view is a POP inside one Activity
 * ([NavHostComposeActivity]'s [androidx.navigation.NavController], not a new Activity instance), so
 * no `onResume` happens and `reconcileReadingStateOnResume` — the only consumer of
 * `updateDocumentsPending` before this fix — never runs. An already-open window kept showing its
 * pre-install "not installed" rendering until some unrelated trip produced a real `onResume` (measured
 * on the device: 75 s late).
 *
 * **Why this drives [NavHostComposeActivity.applyPendingDocumentUpdateOnReturnToReading] directly,
 * not [NavHostComposeActivity.applyReadingReturnDebts].** The two are siblings called from the same
 * `OnDestinationChangedListener`, not nested — `readingReturnDebts` answers "what did THIS PARTICULAR
 * LAUNCH owe", a request-code question; this fix is flag-driven, keyed on `updateDocumentsPending`
 * alone, because `DocumentChanges.installedChanged` is notified from six destinations plus `doDownload`
 * and what matters is that documents changed, not who launched. `ReadingHostNonStdResultTest`'s
 * `composedHost().apply { … }.applyReadingReturnDebts(NavRoutes.READING)` pattern is the precedent for
 * driving one of this listener's internal arms directly without a composed `NavController`; this test
 * follows it for the sibling arm F57 adds.
 *
 * **No `windowSync` call-count spy.** `WindowSync` is a final Kotlin class and this module's Mockito
 * (3.12.4, subclass mock maker only — no `mockito-inline`) cannot spy a final class, so the assertion
 * instead uses the same real, synchronous effect `ReadingHostResumeReconciliationTest` and
 * `ReadingDestinationInGraphTest.theComposedReadingViewsWindowsGetTheirInitialContentLoad` already rely
 * on: `reloadAllWindows(true)` drives `Window.updateOrScroll()` -> `loadText()`, which assigns
 * `displayedKey` synchronously before handing the rest to `Dispatchers.IO`. Nulling it and watching it
 * come back is a real reload, not a proxy for one — and is strictly stronger than a bare call count,
 * since a spy would not tell you the reload actually reached a window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingDocumentRefreshOnReturnTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /**
     * A host on the reading route, composed: `.visible()` is what composes the destination and is
     * what makes [NavHostComposeActivity.readingAppBootstrapped] (reached here only via reflection,
     * indirectly, through the effects it gates) true.
     *
     * `firstTime` is pinned false first — the same `SelfLaunchAwaitIntentTest`/`ReadingHostBackChainTest`
     * precaution: it is a file-level `var` in `ActivityBase.kt` that Robolectric does not reset
     * between test METHODS in this JVM, and `ActivityBase.fixNightMode()` arms a delayed `recreate()`
     * while it is true.
     */
    private fun composedHost(): ActivityController<NavHostComposeActivity> {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply { create().start().resume().visible() }
    }

    /** `Window.loadText()` assigns this synchronously; see the class kdoc. */
    private fun displayedKeyOf(window: Window): Any? =
        Window::class.java.getDeclaredField("displayedKey").apply { isAccessible = true }.get(window)

    private fun clearDisplayedKey(window: Window) =
        Window::class.java.getDeclaredField("displayedKey").apply { isAccessible = true }.set(window, null)

    /** `NavHostComposeActivity.updateDocumentsPending` is private; reached the same way
     * `CloudDocumentsControllerRebuildIsolationTest` reaches private host state. */
    private fun updateDocumentsPendingOf(activity: NavHostComposeActivity): Boolean =
        NavHostComposeActivity::class.java.getDeclaredField("updateDocumentsPending")
            .apply { isAccessible = true }.get(activity) as Boolean

    @Test
    fun returningToTheReadingViewSpendsAPendingDocumentUpdate() {
        val activity = composedHost().get()
        val window = activity.hostWindowRepository.activeWindow
        assertTrue(window.isVisible, "sanity: the active window is visible, so loadText cannot return early")
        clearDisplayedKey(window)
        assertNull(displayedKeyOf(window), "sanity: the fixture's entry-time load must actually be clearable")

        DocumentChanges.notifyInstalledChanged()
        assertTrue(
            updateDocumentsPendingOf(activity),
            "sanity: the DocumentChanges.installedChanged subscription in subscribeReadingHost must have armed the flag",
        )

        // The graph pops back to the reading destination -- no onResume happens on this path, so
        // reconcileReadingStateOnResume (the only consumer of updateDocumentsPending before F57)
        // never runs.
        activity.applyPendingDocumentUpdateOnReturnToReading(NavRoutes.READING)

        assertNotNull(
            displayedKeyOf(window),
            "a document update that arrived while the graph was elsewhere must be applied on return " +
                "to the reading destination, not held until the next real onResume (F57)",
        )
        assertFalse(
            updateDocumentsPendingOf(activity),
            "the flag must be spent by the return, exactly as reconcileReadingStateOnResume spends it " +
                "on a real onResume -- otherwise the next real onResume would reload a second time",
        )
    }

    /**
     * **The wiring guard (fix round 1).** [returningToTheReadingViewSpendsAPendingDocumentUpdate]
     * proves [NavHostComposeActivity.applyPendingDocumentUpdateOnReturnToReading] does the right
     * thing when called -- it says nothing about whether anything actually calls it. Deleting the
     * wiring line inside the `OnDestinationChangedListener`
     * (`applyPendingDocumentUpdateOnReturnToReading(destination.route)`) left that test green while
     * the real defect came straight back; this test is what a deletion of that one line must fail.
     *
     * Drives the REAL, composed graph through `onNewIntent` -> `navigateToRoute` -> the real
     * `NavController.navigate`, the exact singleTop self-launch path production uses to return from
     * Download/Settings (see `readingReturnDebts`'s kdoc) — the same live pattern
     * `ReadingImePaddingTest.theModeFollowsTheCurrentDestinationNotJustTheStartRoute` already uses to
     * exercise this SAME listener's `applyWindowModeFor` arm. `controller.newIntent(...)` alone,
     * with no `pause()`/`resume()`, is deliberate: that absence of a real `onResume` is the whole of
     * F57's defect.
     *
     * **RED without the wiring** (fix round 1 -- the
     * `applyPendingDocumentUpdateOnReturnToReading(destination.route)` line deleted from the listener,
     * re-run, and restored afterwards):
     * ```
     * ReadingDocumentRefreshOnReturnTest > theRealGraphSpendsAPendingDocumentUpdateOnReturnToReading FAILED
     *     java.lang.AssertionError: a document update that arrived while the graph was elsewhere must
     *     be applied by the REAL listener's return to the reading destination (F57) -- this is what
     *     must fail if applyPendingDocumentUpdateOnReturnToReading(...) is removed from the listener
     * ```
     *
     * **MINOR (fix round 1): "spent exactly once."** The second round trip below arms nothing new and
     * must reload nothing — proving the FLAG, not mere arrival at `reading`, is what gates the reload.
     */
    @Test
    fun theRealGraphSpendsAPendingDocumentUpdateOnReturnToReading() {
        val controller = composedHost()
        val activity = controller.get()
        val window = activity.hostWindowRepository.activeWindow
        assertTrue(window.isVisible, "sanity: the active window is visible, so loadText cannot return early")

        // Away from reading -- the same singleTop self-launch onNewIntent path production uses.
        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.download()))
        shadowOf(Looper.getMainLooper()).idle()

        clearDisplayedKey(window)
        DocumentChanges.notifyInstalledChanged()
        assertTrue(
            updateDocumentsPendingOf(activity),
            "sanity: the DocumentChanges.installedChanged subscription in subscribeReadingHost must have armed the flag",
        )

        // Back to reading -- through the REAL listener, with no onResume anywhere in this path.
        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(
            displayedKeyOf(window),
            "a document update that arrived while the graph was elsewhere must be applied by the REAL " +
                "listener's return to the reading destination (F57) -- this is what must fail if " +
                "applyPendingDocumentUpdateOnReturnToReading(...) is removed from the listener",
        )
        assertFalse(updateDocumentsPendingOf(activity), "the flag must be spent by the real return")

        // MINOR, fix round 1: spent exactly once. Nothing newly pending, so a second round trip must
        // reload nothing.
        clearDisplayedKey(window)
        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.download()))
        shadowOf(Looper.getMainLooper()).idle()
        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(
            displayedKeyOf(window),
            "a return to reading with nothing newly pending must not reload again -- the FLAG, not " +
                "mere arrival at reading, is what gates the reload",
        )
    }
}
