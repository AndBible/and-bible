package net.bible.android.view.activity.page

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.page.screen.PageTiltScroller
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference
import java.time.Duration

/**
 * #3759 / osTicket 3193: with two-step bookmarking, tapping "Bookmark" in the text-selection toolbar must
 * replace it with the "Selection" / "Verses" rows. The swap rebuilds the menu and invalidates the action mode,
 * but a floating toolbar whose selection rect did not move is not obliged to re-read its menu on invalidate: the
 * reporter's Xiaomi (Android 15) kept showing the stale "Bookmark" row (whose tap did nothing) until the toolbar was
 * hidden and shown again by the notification shade taking window focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28, 33])
class BibleViewTwoStepBookmarkMenuTest {
    private lateinit var view: BibleView
    private lateinit var callback: ActionMode.Callback
    private lateinit var mode: ShowOnlyRefreshingToolbarMode

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(context)
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock(), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 }, topOffset2 = { 0 },
            bottomOffsetForWebView = { 0 }, insetsChanges = { error("Unused") },
        )
        val koin = org.koin.core.context.GlobalContext.get()
        view = BibleView(host, callbacks, WeakReference<Window>(null),
            koin.get(), koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        // The wrapper BibleView puts around WebView's own floating selection callback.
        val wrapper = BibleView::class.java.declaredClasses.single { it.simpleName == "ActionModeCallback2" }
        callback = wrapper.getDeclaredConstructor(BibleView::class.java, ActionMode.Callback::class.java).apply {
            isAccessible = true
        }.newInstance(view, mock<ActionMode.Callback>()) as ActionMode.Callback
        mode = ShowOnlyRefreshingToolbarMode(context, callback)
    }

    @After fun cleanup() {
        view.destroy()
    }

    /**
     * A floating selection toolbar that, like the reporter's, re-reads the menu only when it is shown: an
     * [invalidate] runs the callback's prepare step but does not re-lay out the rows it already shows (AOSP lays out
     * again only when the content rect moved or the toolbar is (re)shown). Taps go to the rows on screen.
     */
    private class ShowOnlyRefreshingToolbarMode(
        private val context: Context,
        private val callback: ActionMode.Callback,
    ) : ActionMode() {
        private val menu = MenuBuilder(context)
        private var shownItems: List<MenuItem> = emptyList()
        val shownIds: List<Int> get() = shownItems.map { it.itemId }

        fun start() {
            callback.onCreateActionMode(this, menu)
            callback.onPrepareActionMode(this, menu)
            show()
        }

        private fun show() {
            shownItems = (0 until menu.size()).map(menu::getItem).filter { it.isVisible }
        }

        /** The user taps the row with [id] that the toolbar currently shows. */
        fun tap(id: Int) {
            val item = shownItems.single { it.itemId == id }
            callback.onActionItemClicked(this, item)
        }

        override fun invalidate() {
            callback.onPrepareActionMode(this, menu)
        }

        override fun hide(duration: Long) {
            shownItems = emptyList()
            Handler(Looper.getMainLooper()).postDelayed(::show, maxOf(duration, 0))
        }

        override fun getType(): Int = TYPE_FLOATING
        override fun setTitle(title: CharSequence?) {}
        override fun setTitle(resId: Int) {}
        override fun setSubtitle(subtitle: CharSequence?) {}
        override fun setSubtitle(resId: Int) {}
        override fun setCustomView(view: View?) {}
        override fun finish() { callback.onDestroyActionMode(this) }
        override fun getMenu(): Menu = menu
        override fun getTitle(): CharSequence? = null
        override fun getSubtitle(): CharSequence? = null
        override fun getCustomView(): View? = null
        override fun getMenuInflater(): MenuInflater = MenuInflater(context)
        override fun invalidateContentRect() {}
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    @Test fun tappingBookmarkShowsTheSelectionAndVerseRows() {
        // The page has reported a verse selection (what setCurrentSelection does after querySelection()).
        view.currentSelection = Selection("KJV", 100, 100, "In the beginning")
        view.menuPrepared = true
        mode.start()
        assertEquals(R.id.add_bookmark, mode.shownIds.first())

        mode.tap(R.id.add_bookmark)
        idle()

        assertEquals(listOf(R.id.add_bookmark_selection, R.id.add_bookmark_whole_verse), mode.shownIds)
    }
}
