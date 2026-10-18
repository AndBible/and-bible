@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedcore.nav.NavRoutes

/**
 * The iOS twin of Android's Welcome "gate (b)" (`NavHostComposeActivity.recheckOnReturnToWelcome` +
 * `welcomeAfterFlow`). A flow that leaves Welcome (Download, ...) pops back to Welcome when it finishes; the
 * destination listener reports every destination change to [onDestination]. A change to a destination other than
 * Welcome with Welcome still on the stack arms [awaitingReturn]; the next arrival at Welcome runs the check once:
 * with a usable Bible, make reading ready and open it (only if Welcome is still current after the suspend, so two
 * overlapping checks never push two READING entries); without one, stay and [refreshWelcome]. The first arrival at
 * Welcome (the start destination, nothing armed) does nothing.
 *
 * All callbacks except [usableBible] and [ensureReading] (which run off main inside) are main-thread.
 */
@kotlin.native.HiddenFromObjC
class IosWelcomeReturnGate(
    private val scope: CoroutineScope,
    private val usableBible: suspend () -> Boolean,
    private val ensureReading: suspend () -> Unit,
    private val currentRoute: () -> String?,
    private val openReading: () -> Unit,
    private val refreshWelcome: () -> Unit,
    private val log: (String) -> Unit = {},
) {
    /** True while a flow is out of Welcome (main thread only). */
    var awaitingReturn = false
        private set

    /** [route] is the new current destination's route; [welcomeOnStack] whether a Welcome entry is on the back stack. */
    fun onDestination(route: String?, welcomeOnStack: Boolean) {
        if (route?.substringBefore('?') != NavRoutes.WELCOME) {
            if (welcomeOnStack) awaitingReturn = true
            return
        }
        if (!awaitingReturn) return
        awaitingReturn = false
        scope.launch {
            try {
                if (!usableBible()) {
                    refreshWelcome()
                    return@launch
                }
                ensureReading()
                if (currentRoute()?.substringBefore('?') != NavRoutes.WELCOME) return@launch
                openReading()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("Welcome return check failed: ${e.stackTraceToString()}")
            }
        }
    }
}
