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
package net.bible.android.view.activity.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.service.cloudsync.documents.DocumentSyncProgressEvent

/**
 * Bridges [DocumentSyncProgressEvent]s from [ABEventBus] into a [StateFlow] the Compose Cloud screen
 * observes to drive the loading bar. Mirrors DownloadProgressBridge, but registers via [onMain] so
 * the flow is only mutated on the main looper (the service posts from an IO coroutine). The service
 * posts running=true repeatedly (once per document) and running=false once — a plain boolean, not a
 * counter. The host observes the false transition to trigger a post-transfer re-scan.
 */
class CloudSyncProgressBridge {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun register() {
        ABEventBus.register(this) { onMain<DocumentSyncProgressEvent> { event -> _running.value = event.running } }
    }

    fun unregister() {
        ABEventBus.unregister(this)
    }
}
