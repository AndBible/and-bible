package net.bible.sharedui.app.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CloudDocumentsHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    /** Records every call and answers a string that names it, so a test can assert exactly what was asked. */
    private class RecordingText : PlatformText {
        val calls = mutableListOf<String>()
        override fun formatFileSize(bytes: Long): String {
            calls += "size($bytes)"
            return "SIZE[$bytes]"
        }
        override fun plural(key: PluralKey, count: Int, vararg args: Any): String {
            calls += "plural($key,$count,${args.toList()})"
            return "PLURAL[$key,$count,${args.toList()}]"
        }
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected ${m.name}")
        } as T

    private val strings = iosStrings("en")
    private val text = RecordingText()

    private val host = CloudDocumentsHost(
        shell = shell,
        strings = strings,
        platformText = text,
        platform = unused(),
        helpDialogs = unused(),
        syncStarter = unused(),
        appSettingsProvider = { error("unused") },
        documentSyncSettingsProvider = { error("unused") },
        documentSyncProvider = { error("unused") },
        cloudSyncProvider = { error("unused") },
        restoredFilters = { null },
        consumeRestoredFilters = { },
    )

    @Test
    fun confirmedManualSyncRequestsPermissionButDismissDoesNot() = kotlinx.coroutines.test.runTest {
        var asks = 0
        var transfers = 0
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        val scope = this
        val testShell = object : HostShell by shell { override val hostScope = scope }
        val environment = inertEnvironment()
        val settings = net.bible.service.cloudsync.documents.DocumentSyncSettings(
            { inertProxy<net.bible.android.database.DocumentSyncPreferencesDao>() },
            { error("unused") }, { error("unused") },
            net.bible.sharedcore.platform.OrderedLauncher(scope), environment,
        )
        settings.ensureLoaded()
        val starter = object : net.bible.sharedcore.cloud.DocumentSyncStarter {
            override fun pushDocuments(initials: List<String>) = Unit
            override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) {
                assertEquals(listOf("UPLOAD"), push)
                assertEquals(listOf("DOWNLOAD"), download)
                transfers++
            }
            override fun stop() = Unit
        }
        val host = CloudDocumentsHost(
            shell = testShell, strings = strings, platformText = text, platform = unused(), helpDialogs = unused(),
            syncStarter = starter, appSettingsProvider = { inertSettings() }, documentSyncSettingsProvider = { settings },
            documentSyncProvider = { error("unused") }, cloudSyncProvider = { error("unused") },
            restoredFilters = { null }, consumeRestoredFilters = {},
            notificationPermission = net.bible.sharedcore.platform.NotificationPermission { asks++; pending.await() },
        )
        val controller = host.deps().controllerFor()
        val entryField = host.javaClass.getDeclaredField("cloudDocumentsEntryRef").apply { isAccessible = true }
        val entry = entryField.get(host)
        val present = host.javaClass.declaredMethods.single { it.name == "cloudDocumentsPresentSyncNow" }.apply { isAccessible = true }
        val plan = net.bible.service.cloudsync.documents.SyncPlan(listOf("DOWNLOAD"), listOf("UPLOAD"), emptyList(), 1, 2)
        present.invoke(host, entry, plan)
        controller.dismissSyncNow()
        runCurrent()
        assertEquals(0, asks)
        assertEquals(0, transfers)
        present.invoke(host, entry, plan)
        controller.confirmSyncNow(listOf(true, true, false))
        runCurrent()
        assertEquals(1, asks)
        assertEquals(1, transfers)
        pending.complete(Unit)
    }

    private fun inertEnvironment(): net.bible.sharedcore.platform.AppEnvironment = inertProxy()
    private fun inertSettings(): net.bible.sharedcore.platform.AppSettings = inertProxy()
    private inline fun <reified T : Any> inertProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> args?.lastOrNull() as? Boolean ?: false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                String::class.java -> ""
                else -> null
            }
        } as T

    @Test
    fun countLabel() {
        assertEquals(strings.cloudDocSyncNowCountNone, host.countLabel(0, 4096L))
        assertEquals(emptyList(), text.calls.filter { it.startsWith("plural") })

        assertEquals(
            text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT_SIZE, 3, 3, text.formatFileSize(2048)),
            host.countLabel(3, 2048L),
        )
        assertEquals(text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, 3, 3), host.countLabel(3, null))
        assertEquals(text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, 3, 3), host.countLabel(3, 0L))
    }
}
