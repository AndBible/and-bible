package net.bible.android.control.activity

import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsCategories
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.NotificationPermission
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionCallTest {
    private inline fun <reified T : Any> inert(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, args ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> (args?.lastOrNull() as? Boolean) ?: false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            String::class.java -> ""
            else -> null
        }
    } as T

    @Test fun syncEnableAsksPermission() = runTest {
        var asks = 0
        val service = service(this, NotificationPermission { asks++ })
        runCurrent()
        assertEquals(0, asks, "construction must not request permission")
        service.setCategoryEnabled("sync_enable_bookmarks", true)
        runCurrent()
        assertEquals(1, asks)
    }

    @Test fun pendingPermissionDoesNotBlockSync() = runTest {
        var starts = 0
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        val service = service(this, NotificationPermission { pending.await() }) { starts++ }
        service.setCategoryEnabled("sync_enable_bookmarks", true)
        runCurrent()
        assertEquals(1, starts)
        pending.complete(Unit)
    }

    @Test fun failedPermissionDoesNotCancelSync() = runTest {
        var starts = 0
        val failures = mutableListOf<Throwable>()
        val writeScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        val service = service(this, NotificationPermission { error("permission failed") }, writeScope) { starts++ }
        service.setCategoryEnabled("sync_enable_bookmarks", true)
        runCurrent()
        assertEquals(1, starts)
        assertEquals(1, failures.size)
        writeScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun startupDoesNotAsk() = runTest {
        var asks = 0
        service(this, NotificationPermission { asks++ })
        runCurrent()
        assertEquals(0, asks)
    }

    @Test fun syncDisableDoesNotAsk() = runTest {
        var asks = 0
        val service = service(this, NotificationPermission { asks++ })
        service.setCategoryEnabled("sync_enable_bookmarks", false)
        runCurrent()
        assertEquals(0, asks)
    }

    private fun service(scope: CoroutineScope, permission: NotificationPermission, writeScope: CoroutineScope = scope, onStart: () -> Unit = {}): SyncSettingsServiceImpl {
        val settings = inert<AppSettings>()
        val env = inert<AppEnvironment>()
        val strings = inert<CoreStrings>()
        val adapters = object : CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter = error("not used")
        }
        val documents = DocumentSyncSettings({ inert<net.bible.android.database.DocumentSyncPreferencesDao>() }, { error("not used") }, { error("not used") }, OrderedLauncher(scope), env)
        val starter = inert<DocumentSyncStarter>()
        val provider = DocumentSyncProvider { error("not used") }
        val cloud = CloudSync(AppDialogController(), strings, env, settings, adapters,
            SyncScheduler { onStart(); kotlinx.coroutines.CompletableDeferred(true) }, starter, provider, documents)
        return SyncSettingsServiceImpl(scope, writeScope, documents, cloud, adapters, SyncSettingsCategories(settings), provider, starter,
            inert<SyncSettingsPlatform>(), strings, env, {}, notificationPermission = permission)
    }
}
