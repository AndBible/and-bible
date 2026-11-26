package net.bible.android.view.activity.page

import android.content.Intent
import androidx.core.content.FileProvider
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
    private val exportRoot get() = File(SharedConstants.internalFilesDir, "export/")
    private val htmlRoot get() = File(exportRoot, "html/")

    class ExportActivity : ActivityBase() {
        override val doNotInitializeApp = true
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        activity = activityController.setup().get()
        // Each Robolectric test has a new filesDir; reattach the real provider to refresh its path strategy.
        val providerInfo = activity.packageManager.resolveContentProvider("${activity.packageName}.provider", 0)!!
        FileProvider().attachInfo(activity, providerInfo)
        val host = mock<ReadingHostActivity>()
        lifecycle = LifecycleRegistry.createUnsafe(host)
        lifecycle.currentState = Lifecycle.State.RESUMED
        whenever(host.lifecycle).thenReturn(lifecycle)
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
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
        exportRoot.deleteRecursively()
        // Do not leave this sandbox's filesDir cached for the next Robolectric application.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
        Dispatchers.resetMain()
    }

    @Test fun htmlExportAsksForAnExportDestinationNotABackupDestination() = runTest(dispatcher) {
        BibleViewSurface(view).shareHtml("<p>Compare</p>", "Compare")
        advanceUntilIdle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(activity.getString(R.string.export_destination_title), request.title)
        assertEquals(activity.getString(R.string.export_destination_message), request.message)
    }

    @Test fun saveSuggestsTheSanitisedDocumentNameAndWritesUtf8HtmlInExportDirectory() = runTest(dispatcher) {
        val html = "<!DOCTYPE html><p>אα</p>"
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
        val file = htmlRoot.walkTopDown().single { it.isFile }
        assertEquals("Study Pad  John 1.html", file.name)
        assertEquals(html, file.readText())
    }

    @Test fun nextExportRemovesOldHtmlButKeepsRecentSharesAndOtherExports() = runTest(dispatcher) {
        val old = File(htmlRoot, "old-export/old.html").apply {
            parentFile!!.mkdirs()
            writeText("old")
            makeOld(this)
        }
        val recent = File(htmlRoot, "recent-export/recent.html").apply {
            parentFile!!.mkdirs()
            writeText("recent")
        }
        val other = File(exportRoot, "old.csv").apply {
            writeText("other")
            makeOld(this)
        }

        BibleViewSurface(view).shareHtml("<p>Compare</p>", "Compare")
        advanceUntilIdle()

        assertFalse("stale HTML export copies must not accumulate forever", old.exists())
        assertFalse("expired per-export directories must not accumulate", old.parentFile!!.exists())
        assertEquals("recent", recent.readText())
        assertEquals("other", other.readText())
    }

    @Test fun nextExportDoesNotPruneOldMyDocumentHtml() = runTest(dispatcher) {
        val myDocument = File(exportRoot, "My page.html").apply {
            parentFile!!.mkdirs()
            writeText("my document")
            makeOld(this)
        }

        BibleViewSurface(view).shareHtml("new reading window", "Compare")
        advanceUntilIdle()

        assertTrue("reading-window cleanup must not delete My Document exports", myDocument.exists())
        assertEquals("my document", myDocument.readText())
    }

    @Test fun sameTitleExportsKeepIndependentShareUrisAndContents() = runTest(dispatcher) {
        val first = shareAndGetUri("first export", "Multiple references")
        val second = shareAndGetUri("second export", "Multiple references")

        assertNotEquals("each export needs its own URI", first, second)
        assertEquals("Multiple references.html", first.lastPathSegment)
        assertEquals("Multiple references.html", second.lastPathSegment)
        assertEquals("first export", readSharedHtml(first))
        assertEquals("second export", readSharedHtml(second))
    }

    @Test fun sameTitleMyDocumentIsNotOverwrittenByReadingWindowExport() = runTest(dispatcher) {
        val myDocument = File(exportRoot, "Compare.html").apply {
            parentFile!!.mkdirs()
            writeText("my document")
        }

        val uri = shareAndGetUri("reading window", "Compare")

        assertEquals("my document", myDocument.readText())
        assertEquals("reading window", readSharedHtml(uri))
    }

    @Test fun oldSameTitleExportIsPrunedButFreshContentIsShared() = runTest(dispatcher) {
        val old = File(htmlRoot, "old-export/Compare.html").apply {
            parentFile!!.mkdirs()
            writeText("stale content")
            makeOld(this)
        }

        val uri = shareAndGetUri("fresh content", "Compare")

        assertFalse("the old same-title copy should expire", old.exists())
        assertEquals("Compare.html", uri.lastPathSegment)
        assertEquals("fresh content", readSharedHtml(uri))
    }

    private fun makeOld(file: File) {
        assertTrue(file.setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.shareAndGetUri(html: String, title: String): android.net.Uri {
        BibleViewSurface(view).shareHtml(html, title)
        advanceUntilIdle()
        val pending = dialogs.pending.value!!
        val request = pending.request as AppDialogRequest.Options
        dialogs.respond(pending.id, AppDialogResult.Selected(request.options[0].value))
        advanceUntilIdle()
        val chooser = shadowOf(activity).nextStartedActivityForResult.intent
        val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        return share.getParcelableExtra(Intent.EXTRA_STREAM)!!
    }

    private fun readSharedHtml(uri: android.net.Uri): String =
        activity.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }

    @Test fun shareUsesTheDocumentFilenameAndHtmlMimeType() = runTest(dispatcher) {
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
        assertTrue(uri.path!!.contains("/reading_window_html/"))
        assertEquals("<p>Compare</p>", readSharedHtml(uri))
    }

}
