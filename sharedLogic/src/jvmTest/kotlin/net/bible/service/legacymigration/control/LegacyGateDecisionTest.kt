package net.bible.service.legacymigration.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.LegacyMigrationOutcome
import net.bible.service.legacymigration.LegacyExtras
import net.bible.service.legacymigration.map.MigrationReport

class LegacyGateDecisionTest {

    private fun report() = MigrationReport()

    private fun recover(decision: LegacyGateDecision): LegacyRecoveryModel =
        assertIs<LegacyGateDecision.Recover>(decision).model

    @Test
    fun successfulOutcomesProceed() {
        assertEquals(LegacyGateDecision.Proceed, decideGate(LegacyMigrationOutcome.AlreadyDone))
        assertEquals(LegacyGateDecision.Proceed, decideGate(LegacyMigrationOutcome.NothingToMigrate))
        assertEquals(
            LegacyGateDecision.Proceed,
            decideGate(LegacyMigrationOutcome.Migrated(report(), LegacyExtras(emptyMap(), null, false, false, emptyList()))),
        )
    }

    @Test
    fun rolledBackFailureRecoversAsFailed() {
        val model = recover(decideGate(LegacyMigrationOutcome.Failed(Kind.CORRUPT_STORE, 1, report(), rolledBack = true)))
        assertEquals(LegacyRecoveryModel(RecoveryReason.FAILED, canRestoreBackup = true, canStartFresh = true, tryAgainResetsAttempts = false), model)
    }

    @Test
    fun keychainFailureRecoversAsKeychainUnavailable() {
        val model = recover(decideGate(LegacyMigrationOutcome.Failed(Kind.KEYCHAIN_UNAVAILABLE, 1, report(), rolledBack = true)))
        assertEquals(RecoveryReason.KEYCHAIN_UNAVAILABLE, model.reason)
        assertEquals(true, model.canRestoreBackup)
        assertEquals(true, model.canStartFresh)
        assertEquals(false, model.tryAgainResetsAttempts)
    }

    @Test
    fun unrolledBackFailureIsRollbackPendingForEveryKind() {
        for (kind in Kind.entries) {
            val model = recover(decideGate(LegacyMigrationOutcome.Failed(kind, 1, report(), rolledBack = false)))
            assertEquals(LegacyRecoveryModel(RecoveryReason.ROLLBACK_PENDING, canRestoreBackup = false, canStartFresh = false, tryAgainResetsAttempts = false), model, "kind=$kind")
        }
    }

    @Test
    fun gaveUpRecoversAsGaveUpAndResetsAttempts() {
        val model = recover(decideGate(LegacyMigrationOutcome.GaveUp(2, Kind.CORRUPT_STORE)))
        assertEquals(LegacyRecoveryModel(RecoveryReason.GAVE_UP, canRestoreBackup = true, canStartFresh = true, tryAgainResetsAttempts = true), model)
    }

    @Test
    fun gaveUpWithoutLastKindRecoversAsGaveUp() {
        val model = recover(decideGate(LegacyMigrationOutcome.GaveUp(2, null)))
        assertEquals(RecoveryReason.GAVE_UP, model.reason)
        assertEquals(true, model.tryAgainResetsAttempts)
    }

    @Test
    fun gaveUpAfterKeychainFailureRecoversAsKeychainUnavailable() {
        val model = recover(decideGate(LegacyMigrationOutcome.GaveUp(2, Kind.KEYCHAIN_UNAVAILABLE)))
        assertEquals(RecoveryReason.KEYCHAIN_UNAVAILABLE, model.reason)
        assertEquals(true, model.canRestoreBackup)
        assertEquals(true, model.canStartFresh)
        assertEquals(true, model.tryAgainResetsAttempts)
    }

    @Test
    fun noFailureEverProceeds() {
        for (kind in Kind.entries) {
            for (rolledBack in listOf(true, false)) {
                val decision = decideGate(LegacyMigrationOutcome.Failed(kind, 1, report(), rolledBack))
                assertIs<LegacyGateDecision.Recover>(decision, "kind=$kind rolledBack=$rolledBack")
            }
            assertIs<LegacyGateDecision.Recover>(decideGate(LegacyMigrationOutcome.GaveUp(3, kind)), "gaveUp kind=$kind")
        }
    }
}
