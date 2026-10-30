package net.bible.sharedui.desktop.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings

private const val DAY_MS = 24 * 60 * 60 * 1000L
internal const val CONSENT_KEY = "desktop_update_check"
private const val LAST_CHECK_KEY = "desktop_update_last_check"
private const val ANNOUNCED_KEY = "desktop_update_announced_build"

class DesktopUpdateCheck(
    private val settings: DesktopDeviceSettings,
    private val clock: () -> Long,
    private val fetch: suspend () -> String,
    private val ownBuild: Int,
    private val ownIsProduction: Boolean,
    private val scope: CoroutineScope,
    private val announce: (DesktopRelease) -> Unit,
    private val consent: suspend () -> Boolean? = { null },
) {
    private var running: Job? = null

    @Synchronized fun start() {
        if (running?.isActive == true || running?.isCompleted == false) return
        val job = scope.launch(start = CoroutineStart.LAZY) { check() }
        running = job
        job.start()
    }

    private suspend fun check() {
        when (settings.getString(CONSENT_KEY)) {
            "yes" -> Unit
            "no" -> return
            else -> {
                val answer = consent() ?: return
                if (settings.getString(CONSENT_KEY) != null) return
                settings.putString(CONSENT_KEY, if (answer) "yes" else "no")
                if (!answer) return
            }
        }
        if (settings.getString(CONSENT_KEY) != "yes") return
        val now = clock()
        val last = settings.getLong(LAST_CHECK_KEY, Long.MIN_VALUE)
        if (last != Long.MIN_VALUE && (now < last || now - last < DAY_MS)) return
        settings.putLong(LAST_CHECK_KEY, now)
        try {
            val release = newestOffered(parseDesktopReleases(fetch()), ownBuild, ownIsProduction, settings.getInt(ANNOUNCED_KEY, 0))
            if (release != null && settings.getString(CONSENT_KEY) == "yes") {
                settings.putInt(ANNOUNCED_KEY, release.build)
                announce(release)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DesktopUpdateCheck", "Update check failed")
        }
    }
}
