package net.bible.sharedui.ios.startup

/**
 * Records the launch for crash accounting only when the legacy migration is settled; otherwise the gate's `onResolve`
 * does it later, so a long first migration is not counted as a crash loop. [IosCrashTracker.onLaunch] is idempotent.
 */
internal fun recordLaunchIfSettled(settled: Boolean, tracker: IosCrashTracker) {
    if (settled) tracker.onLaunch()
}
