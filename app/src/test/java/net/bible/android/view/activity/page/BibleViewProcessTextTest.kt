package net.bible.android.view.activity.page

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.page.screen.PageTiltScroller
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.nhaarman.mockitokotlin2.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28, 33])
class BibleViewProcessTextTest {
    private lateinit var view: BibleView
    private lateinit var mode: ActionMode
    private lateinit var menu: MenuBuilder
    private var beforeProcessTextItem: () -> Unit = {}

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val host = mock<ReadingHostActivity>()
        `when`(host.hostContext).thenReturn(context)
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
        menu = object : MenuBuilder(context) {
            override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence): MenuItem {
                if (order >= 100) beforeProcessTextItem()
                return super.add(groupId, itemId, order, title)
            }
        }
        mode = mock()
        `when`(mode.menu).thenReturn(menu)
        `when`(mode.menuInflater).thenReturn(MenuInflater(context))
    }

    @After fun cleanup() {
        view.destroy()
    }

    private fun registerHandler(name: String) {
        val handler = ResolveInfo().apply {
            nonLocalizedLabel = name
            activityInfo = ActivityInfo().apply {
                packageName = "test.process.text"
                this.name = name
            }
        }
        shadowOf(view.context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), handler,
        )
    }

    private fun prepareMenu() {
        view.menuPrepared = true
        val method = BibleView::class.java.getDeclaredMethod("onPrepareActionMenu", ActionMode::class.java, Menu::class.java)
        method.isAccessible = true
        assertEquals(true, method.invoke(view, mode, menu))
    }

    private fun processTextIntents(): List<Intent> = (0 until menu.size()).mapNotNull {
        menu.getItem(it).intent?.takeIf { intent -> intent.action == Intent.ACTION_PROCESS_TEXT }
    }

    private fun assertHandlerIntents(intents: List<Intent>) {
        assertEquals(listOf("Translate", "Dictionary"), intents.map { it.component!!.className })
        for (intent in intents) {
            assertEquals("test.process.text", intent.component!!.packageName)
            assertEquals("text/plain", intent.type)
            assertEquals("selected verse", intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
            assertTrue(intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        }
    }

    @Test fun processTextHandlersKeepTheirTextAndTargets() {
        registerHandler("Translate")
        registerHandler("Dictionary")
        view.currentSelectionText = "selected verse"

        prepareMenu()

        assertHandlerIntents(processTextIntents())
    }

    @Test fun selectionClearedWhilePreparingHandlersDoesNotCrashOrLoseTheText() {
        // The JavaScript bridge can clear the mutable selection between the menu's null check
        // and intent creation. Trigger the same interleaving without timing-dependent threads.
        registerHandler("Translate")
        registerHandler("Dictionary")
        beforeProcessTextItem = { view.readingBridge.selectionCleared() }
        view.currentSelectionText = "selected verse"

        prepareMenu()

        assertNull(view.currentSelectionText)
        assertHandlerIntents(processTextIntents())
    }

    @Test fun noProcessTextItemsWithoutASelection() {
        registerHandler("Translate")
        view.currentSelectionText = null

        prepareMenu()

        assertTrue(processTextIntents().isEmpty())
    }
}
