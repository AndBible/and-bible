package net.bible.service.cloudsync.ios

import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class IosSyncBindingsTest {
    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as String? ?: default
        override fun getLong(key: String, default: Long) = m[key] as Long? ?: default
        override fun getInt(key: String, default: Int) = m[key] as Int? ?: default
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun getDouble(key: String, default: Double) = m[key] as Double? ?: default
        override fun getFloat(key: String, default: Float) = m[key] as Float? ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    private class FakeEnv : AppEnvironment {
        override val isInitialized = true
        override val deviceIdentifier = "dev-1"
        override val isDiscrete = false
        override val isBeta = false
        override val isDebugMode = false
        override val applicationNameMedium = "AndBible"
        override val packageName = "net.bible.test"
        override val versionNumber = 1L
        override val isMeteredNetwork = false
        override val megabytesFree = 1000L
        override val tmpDir = "/tmp"
        override val cacheDir = "/tmp"
        override val dbBackupPath = "/tmp"
        override fun databasePath(name: String) = "/tmp/$name"
        override fun deleteDatabase(name: String) = true
        override val lastCrashTime = 0L
        override val apkPath: String? = null
    }

    private class FakeRefresh : BackgroundRefresh {
        override fun register(handler: suspend () -> Boolean) {}
        override fun schedule(earliestSeconds: Long) {}
        override fun cancel() {}
    }

    /**
     * Types Stream C / SharedBindings provide. `CloudSync` and `DocumentSync` are final classes with heavy
     * constructors and cannot be faked simply; every definition under test only reaches them lazily
     * (inside lambdas run on first sync), so resolution works without them.
     */
    private fun fakeCModule(): Module = module {
        single<AppSettings> { MapSettings() }
        single<AppEnvironment> { FakeEnv() }
        single<SecretStore> { object : SecretStore {
            private val m = mutableMapOf<String, String>()
            override fun get(key: String) = m[key]
            override fun put(key: String, value: String) { m[key] = value }
            override fun remove(key: String) { m.remove(key) }
        } }
        single<UserNotifier> { object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) {}
            override fun showError(message: String, cause: Throwable?) {}
        } }
        single { AppDialogController() }
        single { AppCoroutineScope() }
    }

    private val strings = IosSyncStrings(
        adapterDisplayName = { "name-$it" },
        webDavServerUrlHint = { "https://hint" },
        syncError = { "sync error" },
        webDavMessage = { "msg" },
        ok = { "OK" },
    )

    @Test
    fun allStreamDDefinitionsResolve() {
        val app = koinApplication { modules(fakeCModule(), buildIosSyncBindings(strings, FakeRefresh())) }
        val koin = app.koin
        assertNotNull(koin.get<IosDevicePrefs>())
        assertNotNull(koin.get<SyncSettingsPlatform>())
        val factory = koin.get<CloudAdapterFactory>()
        assertEquals(listOf("ICLOUD_DRIVE", "NEXT_CLOUD", "WEBDAV"), factory.available)
        assertEquals("name-WEBDAV", factory.displayName("WEBDAV"))
        assertNotNull(koin.get<DocumentSyncStarter>())
        assertNotNull(koin.get<SyncScheduler>())
        assertNotNull(koin.get<BackupHost>())
        assertNotNull(koin.get<IosBackgroundSync>())
        app.close()
    }

    @Test
    fun eachCallBuildsAnIndependentModule() {
        val a = koinApplication { modules(fakeCModule(), buildIosSyncBindings(strings, FakeRefresh())) }
        val b = koinApplication { modules(fakeCModule(), buildIosSyncBindings(strings, FakeRefresh())) }
        assertSame(a.koin.get<SyncScheduler>(), a.koin.get<SyncScheduler>())
        assertNotSame(a.koin.get<SyncScheduler>(), b.koin.get<SyncScheduler>())
        a.close(); b.close()
    }
}
