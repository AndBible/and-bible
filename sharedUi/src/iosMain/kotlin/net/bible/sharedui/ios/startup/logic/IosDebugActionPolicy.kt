package net.bible.sharedui.ios.startup

import net.bible.sharedcore.platform.UserNotifier

/** Runtime guard shared by the native debug adapter and its focused tests. */
internal class IosDebugActionPolicy(
    private val isDebug: () -> Boolean,
    private val notifier: UserNotifier,
    private val testMessage: () -> String,
    private val scheduleFatal: (Long, () -> Unit) -> Unit,
) {
    fun crashAppDelayed() {
        if (!isDebug()) return
        scheduleFatal(2000L) { throw IllegalStateException("Task 6 deliberate uncaught debug crash") }
    }

    fun showTestError() {
        if (!isDebug()) return
        notifier.showError(testMessage(), IllegalStateException("Debug test error"))
    }
}
