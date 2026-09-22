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

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.MainBibleActivity
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
 * alone, because `UpdateMainBibleActivityDocuments` is posted from six destinations plus `doDownload`
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

        ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        assertTrue(
            updateDocumentsPendingOf(activity),
            "sanity: the subscription in readingHostSubscriptions must have armed the flag",
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
}
