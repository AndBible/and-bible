package net.bible.android.view.activity.page

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType

/** Restores the pre-crash pane after replacements load, without changing ordinary focus handling. */
internal class RendererRecoveryFocus(private val repository: () -> WindowRepository) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<IdType>()
    private var activeId: IdType? = null
    private var startedAt = 0L
    private var userChoice: IdType? = null
    private val check = object : Runnable {
        override fun run() {
            val windows = repository()
            val loaded = pending.all { id ->
                val pane = windows.getWindow(id) ?: return@all true
                val view = pane.bibleView as? BibleView
                view?.isAttachedToWindow == true && !view.jsChannel.loading && view.contentVisible
            }
            if (!loaded && SystemClock.uptimeMillis() - startedAt < 3000) {
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
            startedAt = SystemClock.uptimeMillis()
            handler.postDelayed(check, 100)
        }
        pending += windowId
    }

    fun userSelected(windowId: IdType) {
        if (activeId != null) userChoice = windowId
    }

    fun clear() {
        handler.removeCallbacks(check)
        pending.clear()
        activeId = null
        userChoice = null
    }
}
