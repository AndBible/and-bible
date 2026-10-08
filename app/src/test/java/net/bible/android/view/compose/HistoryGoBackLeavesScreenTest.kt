package net.bible.android.view.compose

import android.content.Intent
import android.os.Looper
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
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Slice 8 finding M4, TDD'd as spec §5.1 item 3 requires: this test is written against today's
 * `HistoryManager.goBack()` and is RED if the inference is right.
 *
 * The Search / ReadingPlan destinations' only contribution to this path is `setHistoryRoute`, which
 * sets `isIntegrateWithHistoryManager = true`; the test sets that var directly so the fixture does not
 * have to compose a search form. `ReadingViewVisibility.setVisible(false)` is forced for the same
 * reason: in production the reading destination is disposed while another destination is on top,
 * but Robolectric's frame clock need not have finished the crossfade.
 *
 * M4's subject is `HistoryManager.goBack` -> `leaveCurrentScreen`; BACK on a real history-route destination is
 * `HistoryRouteBackTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HistoryGoBackLeavesScreenTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun historyBackFromAnotherDestinationPopsToReadingInsteadOfFinishingTheHost() {
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get()
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController

        nav.navigate(NavRoutes.AI_TOOL_INFO)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        ReadingViewVisibility.setVisible(false)
        activity.isIntegrateWithHistoryManager = true
        val manager: HistoryManager = GlobalContext.get().get()
        manager.addHistoryItem(
            CommonUtils.windowControl.activeWindow,
            Intent(activity, ErrorActivity::class.java).putExtra("description", "probe"),
        )

        activity.goBackInHistory()

        assertFalse(
            "M4: history BACK from a non-reading destination must not finish the one host (the app)",
            activity.isFinishing,
        )
        assertEquals(
            "…it must leave the current screen, i.e. pop back to the reading destination",
            NavRoutes.READING,
            nav.currentDestination?.route,
        )
    }
}
