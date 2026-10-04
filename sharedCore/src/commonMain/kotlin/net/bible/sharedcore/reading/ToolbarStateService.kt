package net.bible.sharedcore.reading

import kotlinx.coroutines.flow.StateFlow

/** Reactive toolbar snapshot for the Compose reading toolbar (iOS-clean). */
interface ToolbarStateService {
    val toolbar: StateFlow<ToolbarState>

    /**
     * Rebuilds [toolbar] from the active window's current page right now, instead of waiting for
     * the next event-bus-driven update on the Android impl. Needed after a mutation none of the
     * subscribed events cover — e.g. cycling the Strongs display mode via
     * `StrongsPreference.handle()` posts none of them, so without an explicit `refresh()` the
     * toolbar's Strongs icon dim state would stay stale until the next scroll/passage/window/speak
     * event.
     */
    fun refresh()
}
