package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import net.bible.sharedui.desktop.reading.DesktopBackDispatcher

/**
 * Desktop: a back handler that may hand the event on. `passThrough` disables this handler for the current event and
 * offers it to the handlers below (and then to the next scene); with none left [DesktopBackDispatcher.back] reports
 * the event as unhandled.
 */
@Composable
actual fun PassThroughBackHandler(enabled: Boolean, onBack: (passThrough: () -> Unit) -> Unit) {
    DesktopBackDispatcher.Attach()
    val dispatcher = DesktopBackDispatcher.currentDispatcher()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentEnabled by rememberUpdatedState(enabled)
    val handler = remember {
        object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, enabled) {
            override fun onBackCompleted() {
                currentOnBack {
                    isBackEnabled = false
                    try {
                        DesktopBackDispatcher.passThrough()
                    } finally {
                        isBackEnabled = currentEnabled
                    }
                }
            }
        }
    }
    SideEffect { handler.isBackEnabled = enabled }
    DisposableEffect(dispatcher) {
        dispatcher?.addHandler(handler)
        onDispose { if (dispatcher != null) handler.remove() }
    }
}
