package net.bible.android.platform

import net.bible.service.cloudsync.CloudAdapter

/**
 * Implemented by `googledrive.GoogleDriveAdapterProvider`, which the factory loads by a constant
 * class name (so R8 keeps class and constructor). Defined here, outside `googledrive/`, so the
 * factory compiles on F-Droid where the implementation is excluded.
 */
interface DriveAdapterProvider {
    fun create(): CloudAdapter
}
