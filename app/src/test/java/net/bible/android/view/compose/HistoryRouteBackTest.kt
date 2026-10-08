package net.bible.android.view.compose

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ErrorActivity
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Spec 2026-10-08 API 36 §3.2: on a destination that publishes a history route, BACK takes the history step
 * BEFORE the NavHost pops, as `ActivityBase.onBackPressed` did. Driven through the dispatcher, the route
 * predictive back takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HistoryRouteBackTest {
    /**
     * Installs the Compose test environment (recomposer + frame clock) so the host's own `setContent` recomposes in a
     * JVM that already ran another composed test; without it only the first such test in the JVM sees its effects
     * (same trap as `ReadingChooserInGraphResultTest`).
     */
    @get:Rule val compose = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    @Test fun aHistoryRouteDestinationStepsHistoryBeforePopping() {
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.setup().get()
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        // The form needs a current document (prepare() returns null otherwise); set one explicitly.
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        CommonUtils.windowControl.activeWindow.pageManager.setCurrentDocumentAndKey(
            kjv, Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 23, 1), addHistoryItem = false,
        )
        // The search form publishes its history route continuously. EPUB_SEARCH would need an installed EPUB (it pops
        // out when there is no current document to search), so the search-form route is used instead.
        nav.navigate(NavRoutes.searchForm())
        idle()
        assertTrue("fixture: the destination published its history route", activity.isIntegrateWithHistoryManager)
        val manager: HistoryManager = GlobalContext.get().get()
        val window = CommonUtils.windowControl.activeWindow
        manager.addHistoryItem(window, Intent(activity, ErrorActivity::class.java).putExtra("description", "probe"))
        // getHistory, not getEntities: the latter lists only KeyHistoryItems and would not see the probe (an
        // IntentHistoryItem); the brief's getEntities fixture read depth 0 before and after.
        val depth = manager.getHistory(window.id).size

        activity.onBackPressedDispatcher.onBackPressed()
        idle()

        assertEquals("BACK must consume one history item first (a bare NavHost pop leaves history untouched)",
            depth - 1, manager.getHistory(window.id).size)
    }
}
