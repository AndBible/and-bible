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
package net.bible.service.cloudsync.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.host.IosBackupHost
import net.bible.service.cloudsync.host.IosDocumentSyncStarter
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.secrets.DesktopSecretStore
import net.bible.sharedcore.platform.desktop.sync.DesktopBackgroundRefresh
import net.bible.sharedcore.platform.desktop.sync.DesktopSyncScheduler
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.ui.dialog.AppDialogController
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The D2b contract: the sync types of the desktop graph resolve to the real desktop classes, not to stubs. */
class DesktopSyncBindingsContractTest {
    /** A do-nothing implementation of a big port interface: strings answer "x", numbers 0, booleans false, others null. */
    private inline fun <reified T : Any> inert(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
        when (m.returnType) {
            String::class.java -> "/tmp"
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    } as T

    private class MapPrefs : DeviceLocalPrefs {
        val map = HashMap<String, String>()
        override fun getString(key: String, default: String?) = map[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key]?.toBoolean() ?: default
        override fun setString(key: String, value: String) { map[key] = value }
        override fun setBoolean(key: String, value: Boolean) { map[key] = value.toString() }
        override fun remove(key: String) { map.remove(key) }
    }

    private fun <T> withGraph(block: (Koin) -> T): T {
        val scope = AppCoroutineScope()
        val app = koinApplication {
            modules(
                module {
                    single<DeviceLocalPrefs> { MapPrefs() }
                    single<AppEnvironment> { inert() }
                    single<AppSettings> { inert() }
                    single<CoreStrings> { inert() }
                    single<UserNotifier> { inert() }
                    single<FilePicker> { inert() }
                    single<FileDelivery> { inert() }
                    single { AppDialogController() }
                    single { scope }
                    single { DesktopExitTasks() }
                    single { DesktopLifecycleEvents() }
                },
                desktopSyncModule(),
            )
        }
        try {
            app.createEagerInstances()
            return block(app.koin)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            app.close()
        }
    }

    @Test fun syncTypesResolveToTheRealDesktopClasses() = withGraph { koin ->
        assertIs<DesktopSecretStore>(koin.get<SecretStore>())
        assertIs<DesktopSyncScheduler>(koin.get<SyncScheduler>())
        assertIs<DesktopCloudAdapterFactory>(koin.get<CloudAdapterFactory>())
        assertIs<DesktopSyncSettingsPlatform>(koin.get<SyncSettingsPlatform>())
        assertIs<IosDocumentSyncStarter>(koin.get<DocumentSyncStarter>())
        assertIs<DesktopBackgroundRefresh>(koin.get<BackgroundRefresh>())
        assertIs<IosBackupHost>(koin.get<BackupHost>())
        assertIs<DesktopSyncDriver>(koin.get<DesktopSyncDriver>())
        assertSame(koin.get<SyncScheduler>(), koin.get<SyncScheduler>())
    }

    @Test fun theDriverStartsWithTheGraphAndRegistersItsExitTask() = withGraph { koin ->
        // Created by createEagerInstances; with the databases not open the exit task finishes at once without touching them.
        runBlocking { withTimeout(5_000) { assertTrue(koin.get<DesktopExitTasks>().runAll(1_000)) } }
    }

    @Test fun onlyNextCloudAndWebDavAreOffered() = withGraph { koin ->
        val factory = koin.get<CloudAdapterFactory>()
        assertEquals(listOf("NEXT_CLOUD", "WEBDAV"), factory.available)
        assertEquals(false, factory.create("NEXT_CLOUD").signedIn)
        assertEquals(false, factory.create("WEBDAV").signedIn)
        assertFailsWith<IllegalStateException> { factory.create("ICLOUD_DRIVE") }
        assertFailsWith<IllegalStateException> { factory.createForBackground("ICLOUD_DRIVE") }
        Unit
    }

    @Test fun stringLookupReadsTheHolderByReflectionAndFallsBackToTheKey() {
        class Table { fun text(key: String) = "en:$key" }
        class Holder { val current = Table() }
        val koin = koinApplication { modules(module { single { Holder() } }) }.koin
        val lookup = reflectiveStringLookup(koin, Holder::class.java.name)
        assertEquals("en:adapters_next_cloud", DesktopSyncTexts(lookup).adapterDisplayName("NEXT_CLOUD"))
        assertEquals("en:adapters_webdav", DesktopSyncTexts(lookup).adapterDisplayName("WEBDAV"))
        assertEquals("en:webdav_server_url_hint", DesktopSyncTexts(lookup).serverUrlHint())
        // No holder in the graph: the key itself, never an exception.
        assertEquals("webdav_server_url_hint", DesktopSyncTexts(reflectiveStringLookup(koinApplication { }.koin, Holder::class.java.name)).serverUrlHint())
    }
}
