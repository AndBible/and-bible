package net.bible.service.cloudsync

import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.database.DocumentSyncPreferencesDao
import net.bible.android.database.SyncDao
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.settings.DocSyncSummaryData
import net.bible.sharedcore.ui.dialog.AppDialogController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnavailableSyncAdapterTest {
    private inline fun <reified T : Any> stub(noinline call: (String, Array<out Any?>) -> Any? = { _, _ -> null }): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray()) ?: when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Long.TYPE -> 0L
                java.lang.Integer.TYPE -> 0
                String::class.java -> ""
                else -> null
            }
        } as T

    private inner class Fixture(val saved: String = "GOOGLE_DRIVE") : AutoCloseable {
        val values = mutableMapOf<String, Any?>("sync_adapter" to saved, "sync_enable_bookmarks" to true)
        val settings = stub<AppSettings> { name, args ->
            when {
                name.startsWith("get") -> values[args[0]] ?: args.getOrNull(1)
                name.startsWith("set") -> { values[args[0] as String] = args[1]; null }
                else -> null
            }
        }
        val escaped = mutableListOf<Throwable>()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> escaped += e })
        val env = stub<AppEnvironment>()
        val strings = stub<CoreStrings>()
        var creates = 0
        var backgroundCreates = 0
        var scheduled = 0
        var transfers = 0
        var loggedIn = true
        val adapter = stub<CloudAdapter> { name, _ -> when (name) {
            "getSignedIn", "signIn" -> loggedIn
            else -> null
        } }
        val factory = object : CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD", "WEBDAV")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter { creates++; return adapter }
            override fun createForBackground(id: String): CloudAdapter { backgroundCreates++; return adapter }
        }
        val documents = DocumentSyncSettings({ stub<DocumentSyncPreferencesDao>() }, { error("unused") }, { error("unused") }, OrderedLauncher(scope), env)
        val starter = stub<DocumentSyncStarter> { name, _ -> if (name == "start") transfers++; null }
        val provider = DocumentSyncProvider { error("Unavailable backend must not open document sync") }
        val cloud = CloudSync(AppDialogController(), strings, env, settings, factory,
            SyncScheduler { scheduled++; CompletableDeferred(true) }, starter, provider, documents)
        fun service(platform: SyncSettingsPlatform = stub()) = SyncSettingsServiceImpl(
            scope, scope, documents, cloud, factory, SyncSettingsCategories(settings) { stub<SyncDao>() },
            provider, starter, platform, strings, env, {},
        )
        override fun close() = scope.cancel()
    }

    @Test fun unavailableOrUnknownStoredAdapterDoesNotCreateForegroundOrBackgroundAdapter() = runBlocking {
        for (id in listOf("GOOGLE_DRIVE", "UNKNOWN", "")) Fixture(id).use { f ->
            assertEquals(false, f.cloud.signIn())
            assertEquals(false, f.cloud.signIn(background = true))
            assertEquals(0, f.creates)
            assertEquals(0, f.backgroundCreates)
            assertFalse(f.cloud.signedIn)
            assertEquals(id, f.values["sync_adapter"])
        }
    }

    @Test fun unavailableAdapterDoesNotScheduleSyncOrTransferDocuments() = runBlocking {
        Fixture().use { f ->
            f.cloud.start(userInitiated = true).await()
            f.cloud.start().await()
            assertEquals(0, f.scheduled)
            val service = f.service()
            service.setCategoryEnabled("sync_enable_bookmarks", true)
            service.setDocumentsEnabled(DocSyncSummaryData(emptyList(), emptyList(), 0, 0))
            assertEquals(0, f.scheduled)
            assertEquals(0, f.transfers)
            assertFalse(service.snapshot.value.documentsEnabled)
        }
    }

    @Test fun unavailableSavedAdapterBlocksAFormerlySignedInAdapter() = runBlocking {
        Fixture("NEXT_CLOUD").use { f ->
            assertEquals(true, f.cloud.signIn(background = true))
            assertTrue(f.cloud.signedIn)
            f.values["sync_adapter"] = "GOOGLE_DRIVE"
            assertFalse(f.cloud.signedIn)
            assertNull(f.cloud.signedInAdapter())
            f.cloud.synchronize(allowInteraction = false)
            assertEquals(0, f.scheduled)
        }
    }

    @Test fun backgroundPassDoesNotStartSyncForAnUnavailableAdapter() = runBlocking {
        Fixture().use { f ->
            var starts = 0
            val policy = BackgroundSyncPolicy(
                { true }, { false }, { 300 }, { 0 }, { 1_000_000 },
                { f.cloud.signedIn }, { f.cloud.signIn(background = true) },
                { starts++; CompletableDeferred(true) },
            )
            policy.runOnce(force = true)
            assertEquals(0, f.backgroundCreates)
            assertEquals(0, starts)
        }
    }

    @Test fun writeFailureIsContainedWithoutCancellingOtherSettingsWork() {
        Fixture("NEXT_CLOUD").use { f ->
            val service = f.service()
            val throwingService = SyncSettingsServiceImpl(
                f.scope, f.scope, f.documents, f.cloud, f.factory,
                SyncSettingsCategories(f.settings) { stub<SyncDao>() }, f.provider,
                stub<DocumentSyncStarter> { name, _ -> if (name == "start") error("transfer unavailable"); null },
                stub(), f.strings, f.env, {},
            )
            throwingService.setDocumentsEnabled(DocSyncSummaryData(listOf("book"), emptyList(), 1, 0))
            assertTrue(f.escaped.isEmpty(), "Write escaped to the desktop exception handler: ${f.escaped}")
            assertTrue(f.scope.coroutineContext[Job]!!.isActive)
            service.refresh()
            assertEquals("NEXT_CLOUD", service.snapshot.value.adapter)
        }
    }

    @Test fun refreshFailureIsContainedAndNextRefreshCanRecover() {
        Fixture("NEXT_CLOUD").use { f ->
            var fail = true
            val platform = stub<SyncSettingsPlatform> { name, _ ->
                if (name == "credential" && fail) error("settings storage unavailable")
                null
            }
            val service = f.service(platform)
            assertTrue(f.escaped.isEmpty(), "Refresh escaped to the desktop exception handler: ${f.escaped}")
            assertTrue(f.scope.coroutineContext[Job]!!.isActive)
            fail = false
            service.refresh()
            assertEquals("NEXT_CLOUD", service.snapshot.value.adapter)
        }
    }
}
