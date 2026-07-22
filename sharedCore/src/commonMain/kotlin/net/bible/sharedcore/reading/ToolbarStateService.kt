package net.bible.sharedcore.reading

import kotlinx.coroutines.flow.StateFlow

/** Reactive toolbar snapshot for the Compose reading toolbar (iOS-clean). */
interface ToolbarStateService {
    val toolbar: StateFlow<ToolbarState>
}
