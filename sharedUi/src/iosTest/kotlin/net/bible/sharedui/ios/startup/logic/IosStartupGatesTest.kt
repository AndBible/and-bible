package net.bible.sharedui.ios.startup

import net.bible.sharedui.app.routeStartsUninitialised
import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosStartupGatesTest {
    @Test fun gatesRunInOrderAndBackupStartsUninitialised() {
        val calls = mutableListOf<String>()
        val calculator = StartupGate.Calculator { calls += "calculator"; true }
        val recovery = StartupGate.CrashRecovery { calls += "crash"; true }
        val gates = IosStartupGates(listOf(calculator, recovery))
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
        val gates = IosStartupGates(listOf(recovery))
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
        val gates = IosStartupGates(listOf(StartupGate.Calculator { checked++; false }))
        gates.advance()
        assertNull(gates.current)
        assertEquals(StartupGateResult.Continue, gates.result)
        gates.advance()
        assertEquals(1, checked)
    }

    @Test fun exitDoesNotEvaluateFollowingGate() {
        val gates = IosStartupGates(listOf(
            StartupGate.Calculator { true },
            StartupGate.CrashRecovery { error("must not run") },
        ))
        gates.advance()
        gates.advance(StartupGateResult.Exit)
        assertEquals(StartupGateResult.Exit, gates.result)
        assertNull(gates.current)
    }
}
