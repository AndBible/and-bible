@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.startup

/** Ordered pre-database gates. I3b inserts legacy migration AFTER crash recovery and BEFORE startup. */
internal sealed interface StartupGate {
    class Calculator(val required: () -> Boolean) : StartupGate
    class CrashRecovery(val required: () -> Boolean) : StartupGate
}

internal enum class StartupGateResult { Continue, OpenBackup, Exit }

/** A single owner advances the chain; neither a screen nor its effects can race normal startup. */
internal class IosStartupGates(private val gates: List<StartupGate>) {
    private var index = 0
    var result: StartupGateResult? = null
        private set
    var current: StartupGate? = null
        private set

    fun returnFromBackup() {
        if (result != StartupGateResult.OpenBackup) return
        result = null
        current = gates.getOrNull(index - 1)
    }

    fun advance(resolution: StartupGateResult = StartupGateResult.Continue) {
        if (result != null) return
        current = null
        if (resolution != StartupGateResult.Continue) {
            result = resolution
            return
        }
        while (index < gates.size) {
            val gate = gates[index++]
            val required = when (gate) {
                is StartupGate.Calculator -> gate.required()
                is StartupGate.CrashRecovery -> gate.required()
            }
            if (required) { current = gate; return }
        }
        result = StartupGateResult.Continue
    }
}
