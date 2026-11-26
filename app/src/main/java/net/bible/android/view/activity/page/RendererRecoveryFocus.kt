package net.bible.android.view.activity.page

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.sharedcore.event.Subscription

/** Restores the pre-crash pane after replacements load, unless another command selects a pane. */
internal class RendererRecoveryFocus(
    private val lifecycle: Lifecycle,
    private val repository: () -> WindowRepository,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<IdType>()
    private var activeId: IdType? = null
    private var loadingSince: Long? = null
    private var recoverySince = 0L
    private val hostObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) clear()
    }
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
            // Missing replacements never start the loading timer, but must not retain the host forever.
            if (!loaded && now - recoverySince < 30_000 &&
                (loadingSince == null || now - loadingSince!! < 10_000)) {
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
        if (lifecycle.currentState == Lifecycle.State.CREATED ||
            lifecycle.currentState == Lifecycle.State.DESTROYED) return
        if (activeId == null) {
            recoverySince = SystemClock.uptimeMillis()
            activeId = repository().activeWindow.id
            lifecycle.addObserver(hostObserver)
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
        lifecycle.removeObserver(hostObserver)
        selectionChanges?.cancel()
        selectionChanges = null
        pending.clear()
        activeId = null
        loadingSince = null
        userChoice = null
    }
}
