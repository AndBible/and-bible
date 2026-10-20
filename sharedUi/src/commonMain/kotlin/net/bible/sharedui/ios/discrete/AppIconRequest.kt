package net.bible.sharedui.ios.discrete

/** Platform-independent request policy; UIKit calls and main-queue scheduling are supplied by the iOS adapter. */
internal class AppIconRequest(
    private val supportsAlternateIcons: () -> Boolean,
    private val currentName: () -> String?,
    private val setIcon: (String?, (Boolean) -> Unit) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
) {
    private var generation = 0
    private var desiredName: String? = null
    private var inFlight = false

    fun apply(discrete: Boolean) {
        desiredName = if (discrete) "CalculatorIcon" else null
        val request = ++generation
        attempt(request, 0)
    }

    private fun attempt(request: Int, retry: Int) {
        if (request != generation || inFlight || !supportsAlternateIcons() || currentName() == desiredName) return
        val name = desiredName
        inFlight = true
        setIcon(name) { success ->
            inFlight = false
            if (request != generation) {
                // A newer preference may have matched the old icon while this setter was still pending.
                attempt(generation, 0)
            } else if (!success && retry < 3) {
                schedule((retry + 1) * 1500L) { attempt(request, retry + 1) }
            }
        }
    }
}
