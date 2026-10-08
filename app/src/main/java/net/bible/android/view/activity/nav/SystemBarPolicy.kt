package net.bible.android.view.activity.nav

import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.EventSource
import androidx.annotation.VisibleForTesting
import android.view.Window
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * What the system bars should look like right now (fix batch 2 §2.4). [lightStatusIcons] `null`
 * means "unknown -- leave the appearance as it is".
 */
data class SystemBarState(val statusVisible: Boolean, val navVisible: Boolean, val lightStatusIcons: Boolean?) {
    /** A hidden bar is always swipe-revealable, as `hide_status_bar`'s own summary promises. */
    val transientBySwipe: Boolean get() = !statusVisible || !navVisible
}

/**
 * The ONE decision (F77, F71's verifiable half):
 *  - fullscreen is a reading-view mode, so it hides both bars only on `reading`;
 *  - `hide_status_bar` hides the status bar on every nav-host destination;
 *  - the status icons follow what is actually under the bar: the page in fullscreen reading (the
 *    toolbar has left composition there), otherwise the top bar that reported its colour.
 */
fun decideSystemBars(
    onReading: Boolean,
    hideStatusBar: Boolean,
    fullScreen: Boolean,
    topBarArgb: Int?,
    pageBackgroundArgb: Int?,
): SystemBarState {
    val immersive = onReading && fullScreen
    val under = if (immersive) pageBackgroundArgb else topBarArgb
    return SystemBarState(
        statusVisible = !immersive && !hideStatusBar,
        navVisible = !immersive,
        lightStatusIcons = under?.let { backgroundIsLight(it) },
    )
}

/** Same rule and 0.45 threshold as `applySystemBarColor`: a light surface wants dark icons. */
fun backgroundIsLight(argb: Int): Boolean = Color(argb).luminance() >= 0.45f

/**
 * The single writer, on the nav host, of system-bar visibility, behaviour and status-icon
 * appearance (the last since fix batch 2 task 5b, through [SystemBarPolicyHost]). Navigation-bar
 * icon appearance is NOT decided here: it keeps its two existing writers, `applySystemBarColor`'s
 * `fillWindowBackground` branch and the host's pane-background block (spec correction C3).
 * `WindowInsetsControllerCompat` on every API level. Below 30 it flips individual
 * `systemUiVisibility` bits instead of assigning the whole field, which is what removes the
 * API 23-29 race that cleared `LIGHT_STATUS_BAR` (F71).
 */
class SystemBarController(private val window: Window) {
    var lastApplied: SystemBarState? = null
        private set

    fun apply(state: SystemBarState) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior =
            if (state.transientBySwipe) WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            else WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (state.statusVisible) c.show(WindowInsetsCompat.Type.statusBars()) else c.hide(WindowInsetsCompat.Type.statusBars())
        if (state.navVisible) c.show(WindowInsetsCompat.Type.navigationBars()) else c.hide(WindowInsetsCompat.Type.navigationBars())
        state.lightStatusIcons?.let { if (c.isAppearanceLightStatusBars != it) c.isAppearanceLightStatusBars = it }
        lastApplied = state
    }
}

/** `hide_status_bar` was written, so hosts re-decide their system bars. */
object SystemBarSettingChanges {
    private var source = EventSource<Unit>()
    val changes: Events<Unit> get() = source
    fun notifyChanged() {
        source.emit(Unit)
    }
    @VisibleForTesting fun resetSubscribersForTest() { source = EventSource() }
}

/**
 * Implemented by the nav host: a top bar's `SyncSystemBars` REPORTS its colour here instead of
 * writing the status-icon appearance itself, so the host's [SystemBarController] stays its single
 * writer (fix batch 2 §2.4). Activities that do not implement it keep the direct write in
 * `applySystemBarColor`.
 */
interface SystemBarPolicyHost {
    fun onTopBarColourReported(argb: Int)
}
