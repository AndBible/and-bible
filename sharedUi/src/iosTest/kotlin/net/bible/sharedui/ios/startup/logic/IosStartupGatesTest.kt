package net.bible.sharedui.ios.startup

import net.bible.sharedui.app.routeStartsUninitialised
import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.sharedui.host.startup.HostStartupGates
import net.bible.sharedui.host.startup.StartupGate
import net.bible.sharedui.host.startup.StartupGateResult
import net.bible.sharedui.host.startup.productionStartupGates

class IosStartupGatesTest {
    @Test fun gatesRunInOrderAndBackupStartsUninitialised() {
        val calls = mutableListOf<String>()
        val calculator = StartupGate.Calculator { calls += "calculator"; true }
        val recovery = StartupGate.CrashRecovery { calls += "crash"; true }
        val gates = HostStartupGates(listOf(calculator, recovery))
        gates.advance()
        assertEquals(listOf("calculator"), calls)
        assertEquals(calculator, gates.current)
        assertNull(gates.result)
        gates.advance()
        assertEquals(listOf("calculator", "crash"), calls)
        assertEquals(recovery, gates.current)
        gates.advance(StartupGateResult.OpenBackup)
        assertEquals(StartupGateResult.OpenBackup, gates.result)
        assertTrue(routeStartsUninitialised(NavRoutes.BACKUP))
        gates.advance()
        assertEquals(StartupGateResult.OpenBackup, gates.result)
    }

    @Test fun backupUpReturnsToRecoveryWithoutRecheckingOrStartingDatabases() {
        var checks = 0
        val recovery = StartupGate.CrashRecovery { checks++; true }
        val gates = HostStartupGates(listOf(recovery))
        gates.advance()
        gates.advance(StartupGateResult.OpenBackup)
        gates.returnFromBackup()
        assertEquals(recovery, gates.current)
        assertNull(gates.result)
        assertEquals(1, checks)
        gates.advance(StartupGateResult.OpenBackup)
        assertEquals(StartupGateResult.OpenBackup, gates.result)
    }

    @Test fun skippedGatesAndContinueFinishOnce() {
        var checked = 0
        val gates = HostStartupGates(listOf(StartupGate.Calculator { checked++; false }))
        gates.advance()
        assertNull(gates.current)
        assertEquals(StartupGateResult.Continue, gates.result)
        gates.advance()
        assertEquals(1, checked)
    }

    @Test fun exitDoesNotEvaluateFollowingGate() {
        val gates = HostStartupGates(listOf(
            StartupGate.Calculator { true },
            StartupGate.CrashRecovery { error("must not run") },
        ))
        gates.advance()
        gates.advance(StartupGateResult.Exit)
        assertEquals(StartupGateResult.Exit, gates.result)
        assertNull(gates.current)
    }

    @Test fun legacyGateSitsBetweenCalculatorAndCrashRecovery() {
        val calculator = StartupGate.Calculator { true }
        val legacy = StartupGate.LegacyMigration { true }
        val recovery = StartupGate.CrashRecovery { true }
        val gates = HostStartupGates(listOf(calculator, legacy, recovery))
        gates.advance()
        assertEquals(calculator, gates.current)
        gates.advance()
        assertEquals(legacy, gates.current)
        gates.advance()
        assertEquals(recovery, gates.current)
        gates.advance()
        assertNull(gates.current)
        assertEquals(StartupGateResult.Continue, gates.result)
    }

    @Test fun legacyGateSkippedWhenSettled() {
        val recovery = StartupGate.CrashRecovery { true }
        val gates = HostStartupGates(listOf(StartupGate.Calculator { false }, StartupGate.LegacyMigration { false }, recovery))
        gates.advance()
        assertEquals(recovery, gates.current)
    }

    @Test fun returnFromBackupAdvancesPastAnUnrequiredGate() {
        var settled = false
        val legacy = StartupGate.LegacyMigration { !settled }
        val gates = HostStartupGates(listOf(legacy))
        gates.advance()
        assertEquals(legacy, gates.current)
        gates.advance(StartupGateResult.OpenBackup)
        settled = true // the restore declined the migration
        gates.returnFromBackup()
        assertNull(gates.current)
        assertEquals(StartupGateResult.Continue, gates.result)
    }

    @Test fun returnFromBackupStillShowsLegacyGateWhenRequired() {
        val legacy = StartupGate.LegacyMigration { true }
        val gates = HostStartupGates(listOf(legacy))
        gates.advance()
        gates.advance(StartupGateResult.OpenBackup)
        gates.returnFromBackup()
        assertEquals(legacy, gates.current)
        assertNull(gates.result)
    }

    @Test fun returnFromBackupKeepsCrashRecoveryLatched() {
        var latched = true
        val recovery = StartupGate.CrashRecovery { latched }
        val gates = HostStartupGates(listOf(recovery))
        gates.advance()
        gates.advance(StartupGateResult.OpenBackup)
        latched = false // would skip the gate if it were re-evaluated
        gates.returnFromBackup()
        assertEquals(recovery, gates.current)
        assertNull(gates.result)
    }

    @Test fun productionGateListIsCalculatorLegacyCrashRecovery() {
        val list = productionStartupGates({ true }, { true }, { true })
        assertEquals(3, list.size)
        assertTrue(list[0] is StartupGate.Calculator)
        assertTrue(list[1] is StartupGate.LegacyMigration)
        assertTrue(list[2] is StartupGate.CrashRecovery)
        // And the chain walks them in that order.
        val gates = HostStartupGates(list)
        gates.advance()
        assertEquals(list[0], gates.current)
        gates.advance()
        assertEquals(list[1], gates.current)
        gates.advance()
        assertEquals(list[2], gates.current)
    }

    @Test fun productionGateListWiresEachPredicateToItsOwnGate() {
        val calls = mutableListOf<String>()
        val list = productionStartupGates({ calls += "calc"; false }, { calls += "legacy"; false }, { calls += "crash"; false })
        HostStartupGates(list).advance()
        assertEquals(listOf("calc", "legacy", "crash"), calls)
    }
}
