package net.bible.android.view.activity.page

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.lang.ref.WeakReference

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewSurfaceHtmlExportTest {
    private val dispatcher = StandardTestDispatcher()
    private val activityController = Robolectric.buildActivity(ExportActivity::class.java)
    private lateinit var activity: ExportActivity
    private lateinit var view: BibleView
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var dialogs: AppDialogController
    private val exportedFiles = mutableListOf<File>()

    class ExportActivity : ActivityBase() {
        override val doNotInitializeApp = true
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        activity = activityController.setup().get()
        // Each Robolectric test has a new filesDir; reattach the real provider to refresh its path strategy.
        val providerInfo = activity.packageManager.resolveContentProvider("${activity.packageName}.provider", 0)!!
        androidx.core.content.FileProvider().attachInfo(activity, providerInfo)
        val host = mock<ReadingHostActivity>()
        lifecycle = LifecycleRegistry.createUnsafe(host)
        lifecycle.currentState = Lifecycle.State.RESUMED
        whenever(host.lifecycle).thenReturn(lifecycle)
        whenever(host.hostContext).thenReturn(WebViewResourcesContext(RuntimeEnvironment.getApplication()))
        val callbacks = BibleViewHostCallbacks(
            hostActivity = activity, onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 }, topOffset2 = { 0 },
            bottomOffsetForWebView = { 0 }, insetsChanges = { error("Unused") },
        )
        val koin = GlobalContext.get()
        dialogs = koin.get()
        view = BibleView(host, callbacks, WeakReference<Window>(null),
            koin.get(), koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
    }

    @After fun cleanup() {
        dialogs.cancelAll()
        lifecycle.currentState = Lifecycle.State.DESTROYED
        view.destroy()
        activityController.pause().stop().destroy()
        dispatcher.scheduler.runCurrent()
        exportedFiles.forEach { it.delete() }
        Dispatchers.resetMain()
    }

    @Test fun htmlExportAsksForAnExportDestinationNotABackupDestination() = runTest(dispatcher) {
        exportedFiles += File(SharedConstants.internalFilesDir, "export/Compare.html")
        BibleViewSurface(view).shareHtml("<p>Compare</p>", "Compare")
        advanceUntilIdle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(activity.getString(R.string.export_destination_title), request.title)
        assertEquals(activity.getString(R.string.export_destination_message), request.message)
    }

    @Test fun saveSuggestsTheSanitisedDocumentNameAndWritesUtf8HtmlInExportDirectory() = runTest(dispatcher) {
        val html = "<!DOCTYPE html><p>אα</p>"
        val file = File(SharedConstants.internalFilesDir, "export/Study Pad  John 1.html")
        exportedFiles += file
        BibleViewSurface(view).shareHtml(html, "../Study Pad: John 1")
        advanceUntilIdle()
        val pending = dialogs.pending.value!!
        val request = pending.request as AppDialogRequest.Options
        dialogs.respond(pending.id, AppDialogResult.Selected(request.options[1].value))
        advanceUntilIdle()

        val chooser = shadowOf(activity).nextStartedActivityForResult.intent
        val save = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, save.action)
        assertEquals("text/html", save.type)
        assertEquals("Study Pad  John 1.html", save.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue("HTML exports must not use the backup directory", file.isFile)
        assertEquals(html, file.readText())
    }

    @Test fun nextExportRemovesOldHtmlButKeepsRecentSharesAndOtherExports() = runTest(dispatcher) {
        val dir = File(SharedConstants.internalFilesDir, "export/").apply { mkdirs() }
        val old = File(dir, "old.html").apply {
            writeText("old")
            assertTrue(setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L))
        }
        val recent = File(dir, "recent.html").apply { writeText("recent") }
        val other = File(dir, "old.csv").apply {
            writeText("other")
            assertTrue(setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L))
        }
        exportedFiles += listOf(old, recent, other, File(dir, "Compare.html"))

        BibleViewSurface(view).shareHtml("<p>Compare</p>", "Compare")
        advanceUntilIdle()

        assertFalse("stale HTML export copies must not accumulate forever", old.exists())
        assertEquals("recent", recent.readText())
        assertEquals("other", other.readText())
    }

    @Test fun shareUsesTheDocumentFilenameAndHtmlMimeType() = runTest(dispatcher) {
        exportedFiles += File(SharedConstants.internalFilesDir, "export/Compare.html")
        BibleViewSurface(view).shareHtml("<p>Compare</p>", "Compare")
        advanceUntilIdle()
        val pending = dialogs.pending.value!!
        val request = pending.request as AppDialogRequest.Options
        dialogs.respond(pending.id, AppDialogResult.Selected(request.options[0].value))
        advanceUntilIdle()

        val chooser = shadowOf(activity).nextStartedActivityForResult.intent
        val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("text/html", share.type)
        assertEquals("Compare", share.getStringExtra(Intent.EXTRA_SUBJECT))
        val uri = share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("Compare.html", uri.lastPathSegment)
        assertTrue(uri.path!!.contains("/exported_documents/"))
    }

    /** BibleView resolves WebView-owned menu resources during construction. */
    private class WebViewResourcesContext(base: Context) : ContextWrapper(base) {
        @Suppress("DEPRECATION")
        private val res = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getResourceEntryName(resid: Int): String = when (resid) {
                33620236 -> "select_action_menu_select_all"
                33620231 -> "select_action_menu_copy"
                else -> base.resources.getResourceEntryName(resid)
            }
        }
        override fun getResources(): Resources = res
        override fun getApplicationContext(): Context = this
    }
}
