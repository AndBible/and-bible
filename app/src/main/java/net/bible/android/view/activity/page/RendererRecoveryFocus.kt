package net.bible.android.view.activity.page

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.sharedcore.event.Subscription

/** Restores the pre-crash pane after replacements load, unless another command selects a pane. */
internal class RendererRecoveryFocus(private val repository: () -> WindowRepository) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<IdType>()
    private var activeId: IdType? = null
    private var loadingSince: Long? = null
    private var userChoice: IdType? = null
    private var focusDriven = false
    private var selectionChanges: Subscription? = null
    private val check = object : Runnable {
        override fun run() {
            val windows = repository()
            var mountedLoading = false
            var loaded = true
            for (id in pending) {
                val pane = windows.getWindow(id) ?: continue
                val view = pane.bibleView as? BibleView
                val attached = view?.isAttachedToWindow == true
                if (!pane.isVisible && !attached) continue
                val ready = attached && !view!!.jsChannel.loading && view.contentVisible
                if (!ready) loaded = false
                if (pane.isVisible && attached && !ready) mountedLoading = true
            }
            val now = SystemClock.uptimeMillis()
            if (!mountedLoading) loadingSince = null
            else if (loadingSince == null) loadingSince = now
            if (!loaded && (loadingSince == null || now - loadingSince!! < 10_000)) {
                handler.postDelayed(this, 100)
                return
            }
            val target = windows.getWindow(userChoice ?: activeId) ?: windows.activeWindow
            clear()
            // The setter also requests focus, including when the active id already matches.
            windows.activeWindow = target
        }
    }

    fun rendererGone(windowId: IdType) {
        if (activeId == null) {
            activeId = repository().activeWindow.id
            // Synchronous delivery keeps the source marker scoped to exactly the focus setter.
            selectionChanges = repository().activeWindowSelections.subscribe { window ->
                if (!focusDriven) userChoice = window.id
            }
            handler.postDelayed(check, 100)
        }
        if (pending.add(windowId)) loadingSince = null
    }

    fun focusChanged(window: Window) {
        focusDriven = true
        try {
            repository().activeWindow = window
        } finally {
            focusDriven = false
        }
    }

    fun clear() {
        handler.removeCallbacks(check)
        selectionChanges?.cancel()
        selectionChanges = null
        pending.clear()
        activeId = null
        loadingSince = null
        userChoice = null
    }
}
