/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.page.screen

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.event.passage.PassageChangeStartedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WebViewsBuiltEvent
class AfterRemoveWebViewEvent

/**
 * Create Views for displaying documents
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentViewManager (val mainBibleActivity: MainBibleActivity) : KoinComponent {
    val windowControl: WindowControl by inject()
    private val parent: LinearLayout = mainBibleActivity.findViewById(R.id.mainBibleView)
    private var lastView: View? = null
    var splitBibleArea: SplitBibleArea? = null

    /**
     * When on, [ComposeReadingViewHost] owns `parent` (the same `R.id.mainBibleView` container)
     * via a mounted `ComposeView`, so [buildView]/[removeView] must not rebuild a classic
     * [SplitBibleArea] on top of / instead of the ComposeView — otherwise a
     * `NumberOfWindowsChangedEvent`/`PassageChangeStartedEvent` (both trigger [buildView] via the
     * `init` subscription below) would do exactly that. Ordinary window-count/minimise changes are
     * handled by the `WindowStateService` StateFlow + `key(window.id)` recomposition (existing
     * windows keep their live cached BibleView; new/closed windows get new/removed keys) — no
     * BibleViews were destroyed, so no remount is wanted there. But [buildView]'s `forceUpdate=true`
     * callers (`MainBibleActivity.currentWorkspaceId`'s setter, `MainBibleAfterRestore`) call
     * `removeView()` -> `bibleViewFactory.clear()` (destroys every cached BibleView) ->
     * `windowRepository.loadFromDb()` -> `buildView(forceUpdate = true)` even when the workspace
     * (and so its window ids) is unchanged — classic recreates via `SplitBibleArea.update(true)`;
     * on the compose path we mirror that by bumping [ComposeReadingViewHost.rebuild], which forces
     * every pane's `AndroidView` factory to re-run and pick up the freshly-recreated BibleViews.
     */
    private val composeReadingViewActive: Boolean get() =
        CommonUtils.settings.getBoolean("use_compose_ui", false)

	fun destroy() {
        removeView()
        ABEventBus.unregister(this)
        splitBibleArea?.destroy()
    }

    fun removeView() {
        // Compose path: no-op. The BibleViews are actually torn down by `bibleViewFactory.clear()`
        // (called by the same callers, around this), not by this method; the ComposeView subtree
        // itself is recreated afterward via `buildView(forceUpdate = true)` -> `rebuild()` below.
        if (composeReadingViewActive) return
        parent.removeAllViews()
        lastView = null
        ABEventBus.post(AfterRemoveWebViewEvent())
    }

    private fun buildWebViews(forceUpdate: Boolean): SplitBibleArea {
        val topView = splitBibleArea?: SplitBibleArea(mainBibleActivity).also {
            splitBibleArea = it
        }
        topView.update(forceUpdate)
        return topView
    }

    @Synchronized
    fun buildView(forceUpdate: Boolean = false) {
        if (composeReadingViewActive) {
            // Ordinary (forceUpdate=false) window-count changes need no action here — see the
            // `composeReadingViewActive` kdoc. A forced rebuild (post `bibleViewFactory.clear()`)
            // does need one: recreate the Compose pane subtree so `AndroidView`'s factory re-runs.
            if (forceUpdate) {
                mainBibleActivity.composeReadingViewHost?.rebuild()
            }
            return
        }
        val view = buildWebViews(forceUpdate)
        if(lastView != view) {
            removeView()
            lastView = view
            parent.addView(view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        ABEventBus.post(WebViewsBuiltEvent())
    }

    val documentView: BibleView get() = getDocumentView(windowControl.activeWindow)

    private fun getDocumentView(window: Window): BibleView {
        // a specific screen is specified to prevent content going to wrong screen if active screen is changed fast
        return mainBibleActivity.bibleViewFactory.getOrCreateBibleView(window)
    }

    init {
        ABEventBus.register(this) {
            onMain<NumberOfWindowsChangedEvent> { event ->
                buildView()
            }
            // called just before starting work to change the current passage
            onMain<PassageChangeStartedEvent> { event ->
                buildView()
            }
        }
    }
}
