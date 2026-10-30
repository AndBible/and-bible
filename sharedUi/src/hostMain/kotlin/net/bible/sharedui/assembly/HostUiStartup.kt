package net.bible.sharedui.assembly

import kotlinx.coroutines.CoroutineScope

/** The UI waiter joins process startup before selecting recovery bindings or database opening. */
internal suspend fun awaitIosUiStartup(
    ensureStarted: suspend () -> Unit,
    state: HostAppStartupState,
    processScope: CoroutineScope,
    recoveryBackup: Boolean,
    recoveryReady: () -> Unit,
) {
    val startError = try {
        ensureStarted()
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        e
    }
    if (startError != null) state.fail(startError)
    else if (recoveryBackup) recoveryReady()
    else state.launch(processScope)
}
