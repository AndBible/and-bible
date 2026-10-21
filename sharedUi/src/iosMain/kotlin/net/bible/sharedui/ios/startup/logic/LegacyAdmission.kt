package net.bible.sharedui.ios.startup

import net.bible.sharedcore.settings.DeviceLocalPrefs

/**
 * The device-local flag that the legacy-migration gate has been passed (the migration proceeded). Owned by the iOS
 * runner: it is set after a `Proceed` decision and never by common code.
 */
internal const val LEGACY_SETTLED_KEY = "legacy_migration_settled"

/**
 * Whether the legacy migration is settled. Reads only the device-local Boolean (NSUserDefaults on iOS), so it is safe
 * on Main before the first frame: no disk wait (spec C2).
 */
internal fun legacySettled(settings: DeviceLocalPrefs): Boolean = settings.getBoolean(LEGACY_SETTLED_KEY, false)

/**
 * Whether a background task may start the app: file protection allows reading, no crash recovery is pending and the
 * legacy migration is settled. An unsettled migration must only ever run in the foreground, behind its gate.
 */
internal fun backgroundAdmissionAllowed(protectedData: Boolean, crashRecoveryPending: Boolean, settled: Boolean): Boolean =
    protectedData && !crashRecoveryPending && settled

/**
 * Latches, once per process, that normal startup has begun (and with it the database files may be open). The legacy
 * migration renames the Room files, so it must never run after this: a host recreated later in the same process (the
 * settings import row asks for an import, which clears the settled flag) must not re-enter the gate; the import runs at
 * the next cold launch, as the confirmation says.
 */
internal class StartupLatch {
    @kotlin.concurrent.Volatile
    var began: Boolean = false
        private set

    fun markBegan() { began = true }
}

/** The process-wide latch, set by the startup effect right before it starts [net.bible.sharedui.assembly.IosAppStartup]. */
internal val processStartupLatch = StartupLatch()

/** Whether the legacy gate must run now: the migration is not settled AND no startup has begun in this process. */
internal fun legacyGateRequired(settled: Boolean, startupBegan: Boolean): Boolean = !settled && !startupBegan
