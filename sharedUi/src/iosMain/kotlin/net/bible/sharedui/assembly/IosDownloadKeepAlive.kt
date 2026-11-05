package net.bible.sharedui.assembly

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import net.bible.android.control.download.DownloadKeepAlive
import net.bible.sharedcore.log.nsLog
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * Holds a repository transfer inside a UIKit background task (#3973): a backgrounded app keeps running for the
 * time iOS grants (about 30 seconds), so an app switch during the onboarding download does not stop the install.
 * When iOS expires the window the task is ended and the app suspends; an install it did not finish stays recorded
 * by `PendingRepoInstalls` and is reported at the next launch.
 */
internal object IosDownloadKeepAlive : DownloadKeepAlive {
    private val invalid = UIBackgroundTaskInvalid.toLong()

    override fun hold(name: String): () -> Unit {
        val id = AtomicLong(invalid)
        val released = AtomicInt(0)
        val end: () -> Unit = {
            released.value = 1
            val current = id.getAndSet(invalid)
            if (current != invalid) UIApplication.sharedApplication.endBackgroundTask(current.toULong())
        }
        val granted = UIApplication.sharedApplication.beginBackgroundTaskWithName(name) {
            nsLog("download keep-alive expired: $name")
            end()
        }
        id.value = granted.toLong()
        // Expiry (or the release) may already have run, before the id was known: end the granted task now.
        if (released.value == 1) end()
        return end
    }
}
