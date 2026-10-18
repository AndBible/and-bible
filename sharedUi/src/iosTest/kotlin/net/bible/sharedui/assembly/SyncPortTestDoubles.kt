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
package net.bible.sharedui.assembly

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.sync.SyncScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Stand-ins for the four ports Stream D binds (`CloudAdapterFactory`, `SyncSettingsPlatform`, `DocumentSyncStarter`)
 * and `SyncScheduler`. Production phase 1 leaves them unbound; the acceptance test supplies these so every shared
 * definition that depends on them (`CloudSync`, `DocumentSync`, `BookInstallWatcher`, the settings stores, ...) is
 * still resolved. Phase 2 replaces them with `buildIosSyncBindings()`. Test sources only.
 */
fun syncPortTestDoubles(): Module = module {
    single<CloudAdapterFactory> { NoCloudAdapters }
    single<SyncSettingsPlatform> { InMemorySyncSettingsPlatform() }
    single<DocumentSyncStarter> { NoOpDocumentSyncStarter }
    single<SyncScheduler> { SyncScheduler { CompletableDeferred(false) as Deferred<Boolean> } }
}

private object NoCloudAdapters : CloudAdapterFactory {
    override val available: List<String> = emptyList()
    override fun displayName(id: String): String = id
    override fun create(id: String): CloudAdapter = error("test double: no cloud adapter '$id'")
}

private object NoOpDocumentSyncStarter : DocumentSyncStarter {
    override fun pushDocuments(initials: List<String>) = Unit
    override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) = Unit
    override fun stop() = Unit
}

private class InMemorySyncSettingsPlatform : SyncSettingsPlatform {
    private val credentials = mutableMapOf<String, String>()
    override fun credential(key: String): String = credentials[key].orEmpty()
    override fun setCredential(key: String, value: String) { credentials[key] = value }
    override fun isValidUrl(value: String): Boolean = value.startsWith("https://")
    override fun formatFileSize(bytes: Long): String = "$bytes B"
    override fun formatSyncTime(timestamp: Long): String = timestamp.toString()
    override fun formatDecimal2(value: Double): String = value.toString()
    override val webDavState: WebDavStateStore = object : WebDavStateStore {
        override var propagation: Propagation = Propagation.UNKNOWN
        override var certPin: CertPin? = null
    }
    override val webDavServerUrlHint: String = "https://example.invalid/dav"
}
