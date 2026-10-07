/*
 * Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription
import net.bible.sharedcore.navigation.DocInstallStatus

/** Live per-row download status for one document, as consumed by the Compose Download screen. */
data class RowDownloadStatus(val status: DocInstallStatus, val percentDone: Int)

/**
 * Bridges `DownloadControl.progress` into a [StateFlow] the Compose Download screen observes for
 * live per-row progress. Only emissions after [register] arrive (synchronously, on the emitting thread).
 *
 * The statuses carry the Book's `repoIdentity` (not its initials), so [setRepoIdentityMap] must be
 * supplied a repoIdentity -> docId (initials) mapping; statuses for unknown repoIdentities are dropped.
 */
class DownloadProgressBridge {
    private val _statuses = MutableStateFlow<Map<String, RowDownloadStatus>>(emptyMap())
    /** Keyed by docId (Book initials). */
    val statuses: StateFlow<Map<String, RowDownloadStatus>> = _statuses.asStateFlow()

    private var repoIdentityToDocId: Map<String, String> = emptyMap()

    fun setRepoIdentityMap(map: Map<String, String>) {
        repoIdentityToDocId = map
    }

    private var subscription: Subscription? = null

    fun register(progress: Events<DocumentStatus>) {
        subscription?.cancel()
        subscription = progress.subscribe { status -> handle(status) }
    }

    fun unregister() {
        subscription?.cancel()
        subscription = null
    }

    private fun handle(status: DocumentStatus) {
        val docId = repoIdentityToDocId[status.id] ?: return
        val row = RowDownloadStatus(translate(status.documentInstallStatus), status.percentDone)
        _statuses.value = _statuses.value + (docId to row)
    }

    private fun translate(status: DocumentInstallStatus): DocInstallStatus = when (status) {
        DocumentInstallStatus.NOT_INSTALLED -> DocInstallStatus.NOT_INSTALLED
        DocumentInstallStatus.INSTALLED -> DocInstallStatus.INSTALLED
        DocumentInstallStatus.UPGRADE_AVAILABLE -> DocInstallStatus.UPGRADE_AVAILABLE
        DocumentInstallStatus.BEING_INSTALLED -> DocInstallStatus.BEING_INSTALLED
        DocumentInstallStatus.ERROR_DOWNLOADING -> DocInstallStatus.ERROR_DOWNLOADING
        DocumentInstallStatus.INSTALL_CANCELLED -> DocInstallStatus.INSTALL_CANCELLED
    }
}
