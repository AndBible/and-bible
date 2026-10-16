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

package net.bible.android.view.activity.page

import android.content.Intent
import android.os.Environment
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

/**
 * DL-8 (found on device): the note editor's "Go to Add-on downloads" link calls
 * [BibleJavascriptInterface.openDownloads] on WebView's JavaBridge thread. The reading host
 * ([NavHostComposeActivity]) answers that launch by navigating its live graph, which touches a
 * `LifecycleRegistry` and throws "Method setCurrentState must be called on the main thread", so the
 * link did nothing. The launch must reach the host on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class BibleJavascriptInterfaceOpenDownloadsTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private val views = mutableListOf<BibleView>()
    private val storagePath get() = Environment.getExternalStorageDirectory().path

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        // Robolectric reports no free space, which would stop openDownloads at its storage check.
        ShadowStatFs.registerStats(storagePath, 1_000_000, 1_000_000, 1_000_000)
    }

    @After fun tearDown() {
        try {
            views.forEach { it.destroy() }
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
            ShadowStatFs.unregisterStats(storagePath)
        }
    }

    private fun bridgeWithHost(hostActivity: ActivityBase): BibleJavascriptInterface {
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val callbacks = BibleViewHostCallbacks(
            hostActivity = hostActivity, onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 },
            topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Not used") })
        val view = BibleView(host, callbacks, WeakReference(repo.activeWindow), windowControl,
            GlobalContext.get().get(), PageTiltScrollControl(),
            GlobalContext.get().get(), GlobalContext.get().get(),
            GlobalContext.get().get(), GlobalContext.get().get())
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        views += view
        return BibleJavascriptInterface(view)
    }

    @Test fun `openDownloads from the JavaBridge thread launches the add-on downloads on the main thread`() {
        val launches = CopyOnWriteArrayList<Pair<Intent, Boolean>>()
        val hostActivity = mock<ActivityBase>()
        val lifecycle = LifecycleRegistry(hostActivity).apply { currentState = Lifecycle.State.RESUMED }
        whenever(hostActivity.lifecycle).thenReturn(lifecycle)
        whenever(hostActivity.packageName).thenReturn(RuntimeEnvironment.getApplication().packageName)
        doAnswer { inv ->
            // What the real host does here (navigating its graph) is main-thread-only.
            launches += (inv.getArgument<Intent>(0) to (Looper.myLooper() == Looper.getMainLooper()))
            null
        }.whenever(hostActivity).startActivityForResult(any(), any())
        val bridge = bridgeWithHost(hostActivity)

        var bridgeThreadFailure: Throwable? = null
        thread(name = "JavaBridge") {
            try { bridge.openDownloads() } catch (t: Throwable) { bridgeThreadFailure = t }
        }.join(10_000)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("openDownloads threw on the bridge thread", bridgeThreadFailure)
        assertEquals("exactly one launch of the add-on downloads", 1, launches.size)
        val (intent, onMain) = launches.single()
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertTrue("the launch reached the host off the main thread", onMain)
    }
}
