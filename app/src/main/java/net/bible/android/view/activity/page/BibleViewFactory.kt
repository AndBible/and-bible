/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import android.util.Log
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.AndroidTiltSensor
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import java.lang.ref.WeakReference
import java.util.UUID

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Build a new BibleView WebView for a Window
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 *
 * Reading-host re-typing R6a. R5 had to leave this class `MainBibleActivity`-typed and EXCLUDED
 * from [CollaboratorTypeGuardTest]'s scan: the only reason it holds the value at all is to hand the
 * whole thing to [BibleView], and decomposing BibleView's dependency surface was out of R5's
 * mechanical scope. R6a did that decomposition, so this class follows it mechanically — it still
 * only passes the two values on, it just passes R4's narrow [ReadingHostActivity] and a
 * [BibleViewHostCallbacks] bundle instead of the Activity.
 */
class BibleViewFactory(
    val host: ReadingHostActivity,
    val hostCallbacks: BibleViewHostCallbacks,
) : KoinComponent {
    val pageControl: PageControl by inject()
    val windowControl: WindowControl by inject()
    val linkControl: LinkControl by inject()
    val bookmarkControl: BookmarkControl by inject()
    val downloadControl: DownloadControl by inject()
    val searchControl: SearchControl by inject()


    private val windowPageTiltScrollControlMap: MutableMap<Window, PageTiltScrollControl> = java.util.HashMap()
    private fun getPageTiltScrollControl(window: Window): PageTiltScrollControl {
        return windowPageTiltScrollControlMap[window] ?: synchronized(windowPageTiltScrollControlMap) {
            synchronized(windowPageTiltScrollControlMap) {
                windowPageTiltScrollControlMap[window] ?: PageTiltScrollControl(AndroidTiltSensor()) {
                    CommonUtils.windowControl.windowRepository.workspaceSettings.enableTiltToScroll
                }
            }.also {
                windowPageTiltScrollControlMap[window] = it
            }
        }
    }

    private val windowBibleViewMap: MutableMap<IdType, BibleView> = HashMap()

    /** Live views of the workspace switched away from, waiting for an incoming window to take them over (#3950). */
    private val spareViews = ArrayDeque<BibleView>()
    init {
        Log.i(TAG, "New BibleViewFactory ${this.hashCode()}")// ${Log.getStackTraceString(Exception())}")
    }
    
    fun getOrCreateBibleView(window: Window): BibleView = resolveView(
        window, windowBibleViewMap,
        rebind = {
            // Update window reference (window objects are created when loading from db, but id's are same)
            it.window = window
            window.bibleView = it
            it.listenEvents = true
        },
        adopt = { takeSpare(window)?.also { it.adoptWindow(window) } },
        create = {
            val pageTiltScrollControl = getPageTiltScrollControl(window)
            val bibleView = BibleView(this.host, this.hostCallbacks, WeakReference(window), windowControl,
                pageControl, pageTiltScrollControl, linkControl, bookmarkControl, downloadControl, searchControl)
            val bibleJavascriptInterface = BibleJavascriptInterface(bibleView)
            Log.i(TAG, "Creating new BibleView ${this.hashCode()} ${window.id}")
            bibleView.setBibleJavascriptInterface(bibleJavascriptInterface)
            bibleView.initialise()
            // By view, not by the creating window's id: a reused view serves another window by then.
            bibleView.onDestroy = {
                windowBibleViewMap.values.remove(bibleView)
                spareViews.remove(bibleView)
            }
            bibleView
        },
        assign = { window.bibleView = it },
        load = { window.loadText() },
    )

    fun crashAll() {
        Log.i(TAG, "crashAll")
        for (it in windowBibleViewMap) {
            it.value.loadUrl("chrome://crash")
        }
    }

    fun clear() {
        Log.i(TAG, "clear")
        for (it in windowBibleViewMap) {
            val bw = it.value
            bw.onDestroy = null
            bw.doDestroy()
        }
        windowBibleViewMap.clear()
        releaseSpares()
    }

    /**
     * A workspace switch (#3950): instead of destroying the outgoing views, park them so the incoming windows take
     * them over in [getOrCreateBibleView]. Destroying them and booting new WebViews (page load, the page's start-up,
     * then the document) left every pane blank for 0.7-1.5 s on the emulator; a reused view keeps its old page on
     * screen until the new document replaces it in one step. Views no incoming window takes are dropped by
     * [releaseSpares] once the new panes are composed.
     */
    fun retireForReuse() {
        Log.i(TAG, "retireForReuse ${windowBibleViewMap.size}")
        for (bw in windowBibleViewMap.values) {
            bw.retire()
            spareViews.addLast(bw)
        }
        windowBibleViewMap.clear()
    }

    /** A parked view for [window]: its own old view when the same workspace is reloaded, else the oldest one. */
    private fun takeSpare(window: Window): BibleView? {
        val own = spareViews.firstOrNull { it.windowRef.get()?.id == window.id }
        if (own != null) {
            spareViews.remove(own)
            return own
        }
        return spareViews.removeFirstOrNull()
    }

    /** Destroys the parked views of [retireForReuse] that no window took over. */
    fun releaseSpares() {
        if (spareViews.isEmpty()) return
        Log.i(TAG, "releaseSpares ${spareViews.size}")
        while (spareViews.isNotEmpty()) {
            val bw = spareViews.removeFirst()
            bw.onDestroy = null
            bw.doDestroy()
        }
    }

    companion object {
        /**
         * Cached/adopted/created decision of [getOrCreateBibleView], generic so it can be tested without a
         * real [BibleView]. A cached view is only rebound. Otherwise a parked view is taken over ([adopt],
         * null when none is parked) or a new one is created; either is registered in [map],
         * assigned to the window via [assign], and only THEN, if the window's last load was dropped
         * (see [Window.loadPending]), [load] re-runs it, so the load finds the view.
         */
        internal fun <V : Any> resolveView(
            window: Window,
            map: MutableMap<IdType, V>,
            rebind: (V) -> Unit,
            adopt: () -> V? = { null },
            create: () -> V,
            assign: (V) -> Unit,
            load: () -> Unit,
        ): V {
            map[window.id]?.let { rebind(it); return it }
            val view = adopt() ?: create()
            map[window.id] = view
            assign(view)
            if (window.takeLoadPending()) load()
            return view
        }

        private val BIBLE_WEB_VIEW_ID_BASE = 990
		private val TAG = "BibleViewFactory"
    }
}
