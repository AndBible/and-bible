package net.bible.android.view.activity.page

import android.view.ActionMode
import android.view.MenuItem
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.page.screen.PageTiltScroller
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28, 33])
class BibleViewEditorSelectionTest {
    private lateinit var view: BibleView
    private val mode = mock<ActionMode>()
    private val nativeCallback = mock<ActionMode.Callback>()
    private lateinit var callback: ActionMode.Callback

    @Before fun setup() {
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
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
        view.editingTextInJs = true
        // Exercise the wrapper without relying on Robolectric to open a WebView selection toolbar.
        val wrapper = BibleView::class.java.declaredClasses.single { it.simpleName == "ActionModeCallback2" }
        callback = wrapper.getDeclaredConstructor(BibleView::class.java, ActionMode.Callback::class.java).apply {
            isAccessible = true
        }.newInstance(view, nativeCallback) as ActionMode.Callback
    }

    @After fun cleanup() {
        view.destroy()
    }

    private fun item(id: Int): MenuItem = mock<MenuItem>().also {
        whenever(it.itemId).thenReturn(id)
    }

    @Test fun selectAllKeepsTheNativeSelectionModeOpen() {
        val selectAll = item(android.R.id.selectAll)
        whenever(nativeCallback.onActionItemClicked(mode, selectAll)).thenReturn(true)

        assertTrue(callback.onActionItemClicked(mode, selectAll))

        verify(nativeCallback).onActionItemClicked(mode, selectAll)
        // Finishing here clears the selection that WebView has just expanded to the whole note.
        verify(mode, never()).finish()
    }

    @Test fun otherEditingActionsStillFinishTheSelectionMode() {
        val cut = item(android.R.id.cut)
        whenever(nativeCallback.onActionItemClicked(mode, cut)).thenReturn(true)

        assertTrue(callback.onActionItemClicked(mode, cut))

        verify(nativeCallback).onActionItemClicked(mode, cut)
        verify(mode).finish()
    }

    @Test fun unhandledSelectAllDoesNotCloseTheSelection() {
        val selectAll = item(android.R.id.selectAll)

        assertFalse(callback.onActionItemClicked(mode, selectAll))

        verify(nativeCallback).onActionItemClicked(mode, selectAll)
        verify(mode, never()).finish()
    }
}
