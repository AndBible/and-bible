@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.welcome

import net.bible.sharedcore.platform.files.PickedSource

/** Runs the external Welcome restore without entering reading on failure or cancellation. */
@kotlin.native.HiddenFromObjC
suspend fun restoreWelcomeBackup(
    pick: suspend () -> PickedSource?,
    restore: suspend (PickedSource) -> Boolean,
    reportError: suspend (String, Throwable) -> Unit,
    fallbackError: () -> String,
    afterRestore: () -> Unit,
) {
    val restored = try {
        val source = pick() ?: return
        restore(source)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        reportError(e.message ?: fallbackError(), e)
        false
    }
    if (restored) afterRestore()
}
