package net.bible.sharedui.desktop.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedui.settings.DesktopUpdateInterval

private const val MONTH_MS = 30 * 24 * 60 * 60 * 1000L
internal const val CONSENT_KEY = "desktop_update_check"
internal const val OFFER_BETA_KEY = "desktop_update_offer_beta"
private const val LAST_CHECK_KEY = "desktop_update_last_check"
private const val ANNOUNCED_KEY = "desktop_update_announced_build"
private const val SNOOZE_UNTIL_KEY = "desktop_update_snooze_until"

/** The stored check interval; migrates the legacy `yes`/`no` values to `weekly`/`never` in place. */
internal fun DesktopDeviceSettings.updateInterval(): DesktopUpdateInterval? {
    val stored = getString(CONSENT_KEY)
    val interval = DesktopUpdateInterval.fromStored(stored)
    if (interval != null && stored != interval.value) putString(CONSENT_KEY, interval.value)
    return interval
}

/** Postpones every announcement until [until]; the next check then runs at once, whatever the interval. */
internal fun DesktopDeviceSettings.snoozeUntil(until: Long) {
    putLong(SNOOZE_UNTIL_KEY, until)
    remove(LAST_CHECK_KEY)
}

class DesktopUpdateCheck(
    private val settings: DesktopDeviceSettings,
    private val clock: () -> Long,
    private val fetch: suspend () -> String,
    private val ownBuild: Int,
    private val ownChannel: DesktopChannel,
    private val scope: CoroutineScope,
    private val announce: (DesktopRelease) -> Unit,
    private val consent: suspend () -> Boolean? = { null },
    private val announceAndAwait: suspend (DesktopRelease) -> Boolean = { announce(it); true },
) {
    private var running: Job? = null

    @Synchronized fun start() {
        if (running?.isActive == true || running?.isCompleted == false) return
        val job = scope.launch(start = CoroutineStart.LAZY) { check() }
        running = job
        job.start()
    }

    private suspend fun check() {
        val interval = settings.updateInterval() ?: run {
            val answer = consent() ?: return
            if (settings.getString(CONSENT_KEY) != null) return
            val chosen = if (answer) DesktopUpdateInterval.WEEKLY else DesktopUpdateInterval.NEVER
            settings.putString(CONSENT_KEY, chosen.value)
            chosen
        }
        val intervalMs = interval.intervalMs ?: return
        val now = clock()
        val snoozedUntil = settings.getLong(SNOOZE_UNTIL_KEY, Long.MIN_VALUE)
        if (snoozedUntil != Long.MIN_VALUE && now < snoozedUntil && snoozedUntil - now <= MONTH_MS) return
        val last = settings.getLong(LAST_CHECK_KEY, Long.MIN_VALUE)
        if (last != Long.MIN_VALUE && (now < last || now - last < intervalMs)) return
        settings.putLong(LAST_CHECK_KEY, now)
        try {
            val release = newestOffered(parseDesktopReleases(fetch()), ownBuild, ownChannel, settings.getInt(ANNOUNCED_KEY, 0),
                when (settings.getString(OFFER_BETA_KEY)) { "yes" -> true; "no" -> false; else -> null })
            if (release != null && settings.updateInterval().let { it != null && it != DesktopUpdateInterval.NEVER }) {
                if (announceAndAwait(release)) settings.putInt(ANNOUNCED_KEY, release.build)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DesktopUpdateCheck", "Update check failed")
        }
    }
}
