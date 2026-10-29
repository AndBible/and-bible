package net.bible.service.cloudsync.ios

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * The UIKit background-task calls that [HostDocumentSyncStarter] receives as injected lambdas. Apple documents
 * `beginBackgroundTask`/`endBackgroundTask` as callable from any thread, so there is no main-queue hop.
 */
internal object UiKitBackgroundTask {
    /** The begin call for a task named [name]; its argument is UIKit's expiration handler. */
    fun begin(name: String): (() -> Unit) -> ULong =
        { expire -> UIApplication.sharedApplication.beginBackgroundTaskWithName(name, expire) }

    val end: (ULong) -> Unit = { UIApplication.sharedApplication.endBackgroundTask(it) }

    val invalid: ULong = UIBackgroundTaskInvalid
}
