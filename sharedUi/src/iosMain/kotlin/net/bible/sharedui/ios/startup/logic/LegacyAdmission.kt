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
 * Records the launch for crash accounting only when the legacy migration is settled; otherwise the gate's `onResolve`
 * does it later, so a long first migration is not counted as a crash loop. [IosCrashTracker.onLaunch] is idempotent.
 */
internal fun recordLaunchIfSettled(settled: Boolean, tracker: IosCrashTracker) {
    if (settled) tracker.onLaunch()
}
