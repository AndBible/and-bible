package net.bible.sharedui.desktop.assembly

import net.bible.sharedcore.platform.desktop.boundedBlocking
import java.util.concurrent.CopyOnWriteArrayList

/** Shutdown work with individual budgets, separate from the shared exit-task budget. */
class DesktopPreExitSteps {
    private data class Step(val name: String, val timeoutMs: Long, val run: () -> Unit)
    private val steps = CopyOnWriteArrayList<Step>()

    fun register(name: String, timeoutMs: Long, step: () -> Unit) {
        steps += Step(name, timeoutMs, step)
    }

    fun runAll() {
        steps.forEach { boundedBlocking(it.name, it.timeoutMs, it.run) }
    }
}
