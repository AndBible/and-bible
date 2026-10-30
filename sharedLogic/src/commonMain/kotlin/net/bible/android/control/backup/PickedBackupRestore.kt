/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.backup

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.bible.sharedcore.platform.files.PickedSource

/**
 * Picks a backup and restores it: a failure of either step is reported through [reportError] (with [fallbackError]
 * when it has no message), cancellation propagates unreported, and [afterRestore] runs only after a successful
 * restore. Shared by the Welcome restore (Android and iOS) and [BackupControl.restoreAppDatabaseViaIntent].
 */
suspend fun restorePickedBackup(
    pick: suspend () -> PickedSource?,
    restore: suspend (PickedSource) -> Boolean,
    reportError: suspend (String, Throwable) -> Unit,
    fallbackError: () -> String,
    afterRestore: () -> Unit = {},
) {
    val restored = try {
        val source = pick() ?: return
        restore(source)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        reportError(e.message ?: fallbackError(), e)
        false
    }
    // A committed restore can finish non-cancellably, but a destroyed host must not navigate afterward.
    currentCoroutineContext().ensureActive()
    if (restored) afterRestore()
}
