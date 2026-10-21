package net.bible.service.legacymigration.control

import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationOutcome

/** Why the startup gate cannot proceed past the legacy migration, which decides the recovery screen's wording. */
enum class RecoveryReason { FAILED, GAVE_UP, KEYCHAIN_UNAVAILABLE, ROLLBACK_PENDING }

/**
 * What the recovery screen offers for a blocked migration.
 *
 * [canRestoreBackup] and [canStartFresh] are false for [RecoveryReason.ROLLBACK_PENDING]: the databases directory may
 * still hold half-adopted files, so neither a restore nor a fresh start is safe until the next launch finishes the
 * rollback. [tryAgainResetsAttempts] is true only for [RecoveryReason.GAVE_UP] (and keychain give-ups), where the
 * attempt counter must be cleared before a new run is allowed.
 */
data class LegacyRecoveryModel(
    val reason: RecoveryReason,
    val canRestoreBackup: Boolean,
    val canStartFresh: Boolean,
    val tryAgainResetsAttempts: Boolean,
)

/** The startup gate's decision for a migration outcome: open the app, or show the recovery screen. */
sealed interface LegacyGateDecision {
    /** The databases are settled (migrated, nothing to migrate, or already done); the app may open them. */
    data object Proceed : LegacyGateDecision

    /** The app must not open the databases; show the recovery screen described by [model]. */
    data class Recover(val model: LegacyRecoveryModel) : LegacyGateDecision
}

/**
 * Maps a migration outcome to the startup gate's decision. Only the three successful outcomes proceed; every failure
 * recovers. The order matters: an unrolled-back failure is [RecoveryReason.ROLLBACK_PENDING] whatever its kind, since
 * the databases directory is not in a state the user can restore from or start over in.
 */
fun decideGate(outcome: LegacyMigrationOutcome): LegacyGateDecision = when (outcome) {
    LegacyMigrationOutcome.AlreadyDone,
    LegacyMigrationOutcome.NothingToMigrate,
    is LegacyMigrationOutcome.Migrated -> LegacyGateDecision.Proceed

    is LegacyMigrationOutcome.Failed -> when {
        !outcome.rolledBack -> recover(RecoveryReason.ROLLBACK_PENDING, restorable = false, resetsAttempts = false)
        outcome.kind == LegacyMigrationException.Kind.KEYCHAIN_UNAVAILABLE ->
            recover(RecoveryReason.KEYCHAIN_UNAVAILABLE, restorable = true, resetsAttempts = false)
        else -> recover(RecoveryReason.FAILED, restorable = true, resetsAttempts = false)
    }

    is LegacyMigrationOutcome.GaveUp ->
        if (outcome.lastKind == LegacyMigrationException.Kind.KEYCHAIN_UNAVAILABLE) {
            recover(RecoveryReason.KEYCHAIN_UNAVAILABLE, restorable = true, resetsAttempts = true)
        } else {
            recover(RecoveryReason.GAVE_UP, restorable = true, resetsAttempts = true)
        }
}

private fun recover(reason: RecoveryReason, restorable: Boolean, resetsAttempts: Boolean) =
    LegacyGateDecision.Recover(
        LegacyRecoveryModel(
            reason = reason,
            canRestoreBackup = restorable,
            canStartFresh = restorable,
            tryAgainResetsAttempts = resetsAttempts,
        ),
    )
