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
import net.bible.service.cloudsync.SyncNamespace
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.host.IosBackupHost
import net.bible.service.cloudsync.host.IosDocumentSyncStarter
import net.bible.service.cloudsync.host.IosSyncStrings
import net.bible.service.db.DatabaseContainer
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

    private fun <T> withGraph(appSettings: AppSettings = inert(), appEnvironment: AppEnvironment = inert(), block: (Koin) -> T): T {
        val scope = AppCoroutineScope()
        val app = koinApplication {
            modules(
                module {
                    single<DeviceLocalPrefs> { MapPrefs() }
                    single<AppEnvironment> { appEnvironment }
                    single<AppSettings> { appSettings }
                    single<CoreStrings> { inert() }
                    single {
                        IosSyncStrings(
                            adapterDisplayName = { "name:$it" }, webDavServerUrlHint = { "hint" }, syncError = { "err" },
                            webDavMessage = { "msg" }, ok = { "ok" },
                        )
                    }
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
        assertTrue(koin.get<net.bible.service.cloudsync.CloudSyncNamespace>().value.startsWith("net.bible.android.activity"))
        assertIs<DesktopSyncScheduler>(koin.get<SyncScheduler>())
        assertIs<DesktopCloudAdapterFactory>(koin.get<CloudAdapterFactory>())
        assertIs<DesktopSyncSettingsPlatform>(koin.get<SyncSettingsPlatform>())
        assertIs<IosDocumentSyncStarter>(koin.get<DocumentSyncStarter>())
        assertIs<DesktopBackgroundRefresh>(koin.get<BackgroundRefresh>())
        assertIs<IosBackupHost>(koin.get<BackupHost>())
        assertIs<DesktopSyncDriver>(koin.get<DesktopSyncDriver>())
        assertSame(koin.get<SyncScheduler>(), koin.get<SyncScheduler>())
    }

    private fun recordingSettings(reads: MutableList<String>) =
        Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, m, args ->
            reads += m.name + (args?.firstOrNull() ?: "")
            when (m.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Long.TYPE -> 0L; java.lang.Integer.TYPE -> 0; String::class.java -> ""; else -> null }
        } as AppSettings

    private fun environment(initialized: java.util.concurrent.atomic.AtomicBoolean) =
        Proxy.newProxyInstance(AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java)) { _, m, _ ->
            when {
                m.name == "isInitialized" -> initialized.get()
                m.returnType == String::class.java -> "/tmp"
                m.returnType == java.lang.Boolean.TYPE -> false
                m.returnType == java.lang.Long.TYPE -> 0L
                m.returnType == java.lang.Integer.TYPE -> 0
                else -> null
            }
        } as AppEnvironment

    /**
     * The driver is only created by createEagerInstances (the test never resolves it): its exit task is the only thing that
     * reads the sync settings once the app is ready, so a recorded read proves the registration. The settings booleans answer
     * their defaults until the app is initialised, so with the databases flagged ready but the app NOT initialised the driver
     * must not consult them at all; once initialised it does.
     */
    @Test fun theDriverStartsWithTheGraphAndRegistersItsExitTask() {
        val reads = java.util.concurrent.CopyOnWriteArrayList<String>()
        val initialized = java.util.concurrent.atomic.AtomicBoolean(false)
        val wasReady = DatabaseContainer.ready
        try {
            withGraph(recordingSettings(reads), environment(initialized)) { koin ->
                DatabaseContainer.ready = true
                runBlocking { withTimeout(5_000) { assertTrue(koin.get<DesktopExitTasks>().runAll(2_000)) } }
                assertTrue(reads.isEmpty(), "databases ready but the app not initialised: settings would answer defaults, so not read: $reads")
                initialized.set(true)
                runBlocking { withTimeout(5_000) { assertTrue(koin.get<DesktopExitTasks>().runAll(2_000)) } }
                assertTrue(reads.any { it.startsWith("getBoolean") }, "the exit task ran the close pass check: $reads")
            }
        } finally {
            DatabaseContainer.ready = wasReady
        }
    }

    @Test fun onlyNextCloudAndWebDavAreOffered() = withGraph { koin ->
        val factory = koin.get<CloudAdapterFactory>()
        assertEquals(listOf("NEXT_CLOUD", "WEBDAV"), factory.available)
        assertEquals("name:NEXT_CLOUD", factory.displayName("NEXT_CLOUD"))
        assertEquals(false, factory.create("NEXT_CLOUD").signedIn)
        assertEquals(false, factory.create("WEBDAV").signedIn)
        assertFailsWith<IllegalStateException> { factory.create("ICLOUD_DRIVE") }
        assertFailsWith<IllegalStateException> { factory.createForBackground("ICLOUD_DRIVE") }
        Unit
    }

    @Test fun syncNamespaceSharesWithAndroidReleaseOnlyWhenPackaged() {
        assertEquals("net.bible.android.activity", desktopSyncNamespace("1.2.3", null))
        assertEquals("net.bible.android.activity.debug", desktopSyncNamespace("dev", null))
        assertEquals("net.bible.android.activity.debug", desktopSyncNamespace(null, null))
        assertEquals("net.bible.android.activity.debug", desktopSyncNamespace("1.2.3", ".debug"))
        assertEquals("net.bible.android.activity", desktopSyncNamespace("dev", ""))
        assertEquals("net.bible.android.activity.debug", desktopSyncNamespace("  ", null))
        assertEquals("net.bible.android.activity.debug", desktopSyncNamespace(" dev ", null))
        assertEquals("net.bible.android.activity", SyncNamespace.forAppId(desktopSyncNamespace("1.2.3", null)))
    }

    /** Created eagerly with the graph, and unsubscribed when the graph closes (UserMessages is a global object). */
    @Test fun userMessagesArePresentedWhileTheGraphLivesAndNotAfter() {
        val shown = java.util.concurrent.CopyOnWriteArrayList<String>()
        val notifier = object : net.bible.sharedcore.platform.UserNotifier {
            override fun showInfo(message: String, long: Boolean) { shown += message }
            override fun showError(message: String, cause: Throwable?) { shown += message }
        }
        val scope = AppCoroutineScope()
        val app = koinApplication {
            modules(
                module {
                    single<DeviceLocalPrefs> { MapPrefs() }; single<AppEnvironment> { inert() }; single<AppSettings> { inert() }
                    single<CoreStrings> { inert() }; single<UserNotifier> { notifier }; single<FilePicker> { inert() }
                    single<FileDelivery> { inert() }; single { AppDialogController() }; single { scope }
                    single { DesktopExitTasks() }; single { DesktopLifecycleEvents() }
                    single { IosSyncStrings({ it }, { "" }, { "" }, { "" }, { "" }) }
                },
                desktopSyncModule(),
            )
        }
        try {
            app.createEagerInstances()
            net.bible.android.control.event.UserMessages.toast("while open")
            assertEquals(listOf("while open"), shown)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            app.close()
        }
        net.bible.android.control.event.UserMessages.toast("after close")
        assertEquals(listOf("while open"), shown, "the presenter unsubscribed when the graph closed")
    }
}
