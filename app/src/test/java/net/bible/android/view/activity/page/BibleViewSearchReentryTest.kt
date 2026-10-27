package net.bible.android.view.activity.page

import android.app.Activity
import android.view.ActionMode
import android.view.MenuItem
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import net.bible.android.activity.R
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.sharedcore.nav.NavRoutes
import org.crosswire.jsword.index.IndexStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewSearchReentryTest {
    @Test
    fun selectionSearchAttachesToScheduledAndCreatingIndexesWithItsQuery() {
        val koin = GlobalContext.get()
        val windowControl = koin.get<WindowControl>()
        val book = windowControl.activeWindowPageManager.currentBible.currentDocument!!
        val oldStatus = book.indexStatus
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(activity.get())
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock<ActivityBase>(), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false },
            imeHeight = { 0 }, topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Unused") },
        )
        val view = BibleView(host, callbacks, WeakReference(windowControl.windowRepository.activeWindow),
            windowControl, koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        val menuItem = mock<MenuItem>()
        whenever(menuItem.itemId).thenReturn(R.id.search)
        val action = BibleView::class.java.getDeclaredMethod("onActionMenuItemClicked", ActionMode::class.java, MenuItem::class.java)
            .apply { isAccessible = true }
        view.currentSelectionText = "light"
        try {
            for (status in listOf(IndexStatus.SCHEDULED, IndexStatus.CREATING)) {
                book.indexStatus = status
                action.invoke(view, mock<ActionMode>(), menuItem)
                val intent = shadowOf(activity.get()).nextStartedActivity
                assertEquals(
                    NavRoutes.searchIndexProgress(searchText = " \"light\"", highlightText = "\"light\"", searchDocument = book.initials),
                    intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
                )
            }
        } finally {
            book.indexStatus = oldStatus
            view.destroy()
            activity.pause().stop().destroy()
        }
    }
}
