package net.bible.sharedui.ios.discrete

/** Platform-independent request policy; UIKit calls and main-queue scheduling are supplied by the iOS adapter. */
internal class AppIconRequest(
    private val supportsAlternateIcons: () -> Boolean,
    private val currentName: () -> String?,
    private val setIcon: (String?, (Boolean) -> Unit) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
) {
    private var generation = 0

    fun apply(discrete: Boolean) {
        val request = ++generation
        val name = if (discrete) "CalculatorIcon" else null
        attempt(name, request, 0)
    }

    private fun attempt(name: String?, request: Int, retry: Int) {
        if (request != generation || !supportsAlternateIcons() || currentName() == name) return
        setIcon(name) { success ->
            if (!success && retry < 3 && request == generation) {
                schedule((retry + 1) * 1500L) { attempt(name, request, retry + 1) }
            }
        }
    }
}
