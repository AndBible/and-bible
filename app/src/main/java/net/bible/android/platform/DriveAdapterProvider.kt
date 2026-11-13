package net.bible.android.platform

import net.bible.service.cloudsync.CloudAdapter

/**
 * Builds the Google Drive adapter. Implemented by `googledrive.GoogleDriveAdapterProvider` (Play builds, loaded by a
 * constant class name so R8 keeps class and constructor) and by the Play-free `drivebrowser.BrowserDriveAdapterProvider`.
 * Defined here, outside `googledrive/`, so the factory compiles on F-Droid where the Play implementation is excluded.
 */
interface DriveAdapterProvider {
    fun create(): CloudAdapter

    /** Restores existing credentials only; never starts an interactive sign-in. */
    fun createForBackground(): CloudAdapter = create()
}
