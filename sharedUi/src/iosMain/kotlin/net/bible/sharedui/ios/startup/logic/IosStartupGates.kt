@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.startup

/** Ordered pre-database gates: calculator, legacy migration, crash recovery, then startup. */
internal sealed interface StartupGate {
    class Calculator(val required: () -> Boolean) : StartupGate
    class LegacyMigration(val required: () -> Boolean) : StartupGate
    class CrashRecovery(val required: () -> Boolean) : StartupGate
}

/**
 * The production gate chain, in order: calculator, legacy migration, crash recovery. One function so a test sees the
 * order the app really uses (the legacy migration must follow the calculator, so nothing runs behind the disguise, and
 * precede crash recovery, whose accounting starts only when the migration has resolved).
 */
internal fun productionStartupGates(
    calculatorRequired: () -> Boolean,
    legacyMigrationRequired: () -> Boolean,
    crashRecoveryRequired: () -> Boolean,
): List<StartupGate> = listOf(
    StartupGate.Calculator(calculatorRequired),
    StartupGate.LegacyMigration(legacyMigrationRequired),
    StartupGate.CrashRecovery(crashRecoveryRequired),
)

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
        val gate = gates.getOrNull(index - 1)
        // The legacy gate can be settled by the backup restore: re-check it and move on. CrashRecovery is latched.
        if (gate is StartupGate.LegacyMigration && !gate.required()) {
            current = null
            advance()
            return
        }
        current = gate
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
                is StartupGate.LegacyMigration -> gate.required()
                is StartupGate.CrashRecovery -> gate.required()
            }
            if (required) { current = gate; return }
        }
        result = StartupGateResult.Continue
    }
}
