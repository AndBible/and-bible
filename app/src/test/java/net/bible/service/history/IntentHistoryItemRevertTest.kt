package net.bible.service.history

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.platform.AndroidHistoryPlatform
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.NavResultIntents
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Menu > Bookmarks > tap a bookmark > Back used to crash with `ActivityNotFoundException`: the bookmark pick records
 * its component-less RESULT intent as the history entry, and the classic `Bookmarks` activity that entry once named is
 * gone. Back must re-open the bookmarks list destination of the nav host; an intent nothing can resolve is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class IntentHistoryItemRevertTest {
    private lateinit var repository: WindowRepository
    private lateinit var originalRepository: WindowRepository
    private val controllers = mutableListOf<ActivityController<*>>()
    private val window get() = repository.activeWindow
    private val platform = AndroidHistoryPlatform()

    @Before
    fun setUp() {
        originalRepository = CommonUtils.windowControl.windowRepository
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        HistoryManager.resetInstanceForTest()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        HistoryManager.resetInstanceForTest()
        try {
            DatabaseResetter.resetDatabase(repository.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepository
        }
    }

    /** `checkActivities(true)`: like a device, starting an intent no manifest entry matches throws instead of recording. */
    private fun activity(): ActivityBase {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)
        return Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()
    }

    private fun bookmarkPick(labelNo: Int = 3): Intent = NavResultIntents.forBookmarks(
        BookmarkResult(verse = "Gen.1.1", description = "Genesis 1:1", labelNo = labelNo, listPosition = 4)
    )

    @Test
    fun backFromABookmarkReopensTheBookmarksListOfTheNavHost() {
        val current = activity()
        val item = assertNotNull(platform.screenHistoryItem(window, bookmarkPick()))
        assertEquals("Genesis 1:1", item.description)

        item.revertTo()

        val started = assertNotNull(shadowOf(current).nextStartedActivityForResult).intent
        assertEquals(NavHostComposeActivity::class.java.name, started.component?.className)
        assertEquals(NavRoutes.bookmarks(3), started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun anIntentWithoutAnyResolvableTargetIsSkippedNotThrown() {
        val current = activity()
        val gone = Intent().setClassName(current, "net.bible.android.view.activity.bookmark.GoneActivity")
            .putExtra("description", "gone")
        val item = assertNotNull(platform.screenHistoryItem(window, gone))

        item.revertTo()

        assertNull(shadowOf(current).nextStartedActivityForResult)
    }

    @Test
    fun skippingAnUnresolvableItemLeavesTheHistoryStackIntact() {
        val current = activity()
        val manager = HistoryManager(CommonUtils.windowControl, platform)
        manager.addHistoryItem(window, bookmarkPick(1))
        manager.addHistoryItem(window, Intent().setClassName(current, "no.such.Activity").putExtra("description", "gone"))
        assertEquals(2, manager.getHistory(window.id).size)

        manager.goBack()
        assertEquals(1, manager.getHistory(window.id).size)
        assertNull(shadowOf(current).nextStartedActivityForResult)

        manager.goBack()
        assertEquals(0, manager.getHistory(window.id).size)
        val started = assertNotNull(shadowOf(current).nextStartedActivityForResult).intent
        assertEquals(NavRoutes.bookmarks(1), started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }
}
