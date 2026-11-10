package net.bible.sharedui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import java.lang.reflect.Proxy
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.bible.android.database.DocumentSyncPreferencesDao
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsCategories
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.desktop.DesktopCloudAdapterFactory
import net.bible.service.cloudsync.host.HostCloudAdapterFactory
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import okio.Path.Companion.toPath
import net.bible.sharedui.assembly.hostSyncStrings
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsLabels
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class SyncSettingsIntegrationTest {
    private inline fun <reified T : Any> inert(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            String::class.java -> ""
            else -> null
        }
    } as T

    @Test fun unavailableStoredAdapterDoesNotCrashSettings() {
        val values = mutableMapOf<String, Any?>("sync_adapter" to "GOOGLE_DRIVE")
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, m, a ->
            val args = a ?: emptyArray()
            when {
                m.name.startsWith("set") -> { values[args[0] as String] = args[1]; null }
                m.name.startsWith("get") -> values[args[0] as String] ?: args.getOrNull(1)
                else -> null
            }
        } as AppSettings
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val env = inert<AppEnvironment>()
        val strings = KeyedCoreStrings(hostStringTable("en"))
        val platform = inert<SyncSettingsPlatform>()
        val adapters = DesktopCloudAdapterFactory(HostCloudAdapterFactory(
            platform, "device", hostSyncStrings(hostStringTable("en")).adapterDisplayName,
            { error("unused") }, inert<WebDavSignInUi>(), "/tmp".toPath(), { _, _ -> error("unused") },
        ))
        val documents = DocumentSyncSettings({ inert<DocumentSyncPreferencesDao>() }, { error("unused") }, { error("unused") }, OrderedLauncher(scope), env)
        val starter = inert<DocumentSyncStarter>()
        val provider = DocumentSyncProvider { error("unused") }
        val cloud = CloudSync(AppDialogController(), strings, env, settings, adapters, inert<SyncScheduler>(), starter, provider, documents)
        try {
            val service = SyncSettingsServiceImpl(scope, scope, documents, cloud, adapters, SyncSettingsCategories(settings) { inert<net.bible.android.database.SyncDao>() }, provider, starter, platform, strings, env, {})
            assertEquals("NEXT_CLOUD", service.snapshot.value.adapter)
            assertEquals("GOOGLE_DRIVE", values["sync_adapter"])
            values["sync_adapter"] = "UNRECOGNIZED"
            service.refresh()
            assertEquals("NEXT_CLOUD", service.snapshot.value.adapter)
            values["sync_adapter"] = "WEBDAV"
            assertEquals("WEBDAV", cloud.adapterId)
            values["sync_adapter"] = null
            assertEquals("NEXT_CLOUD", cloud.adapterId)
        } finally { scope.coroutineContext[Job]?.cancel() }
    }

    @Test fun nextcloudPolicyAndSavedSettingsReachComposeAfterRecreation() = runComposeUiTest {
        val values = mutableMapOf<String, Any?>("sync_adapter" to "GOOGLE_DRIVE", "sync_enable_bookmarks" to true)
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, m, a ->
            val args = a ?: emptyArray()
            when {
                m.name.startsWith("set") -> { values[args[0] as String] = args[1]; null }
                m.name.startsWith("get") -> values[args[0] as String] ?: args.getOrNull(1)
                else -> null
            }
        } as AppSettings
        val credentials = mutableMapOf<String, String>()
        val platform = Proxy.newProxyInstance(SyncSettingsPlatform::class.java.classLoader, arrayOf(SyncSettingsPlatform::class.java)) { _, m, a ->
            val args = a ?: emptyArray()
            when (m.name) {
                "credential" -> credentials[args[0]] ?: ""
                "setCredential" -> { credentials[args[0] as String] = args[1] as String; null }
                "isValidUrl" -> runCatching { URI(args[0] as String).host != null }.getOrDefault(false)
                else -> ""
            }
        } as SyncSettingsPlatform
        val adapters = object : CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD", "WEBDAV")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter = error("sign-in is not used")
        }
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val env = inert<AppEnvironment>()
        val strings = KeyedCoreStrings(hostStringTable("en"))
        val documents = DocumentSyncSettings({ inert<DocumentSyncPreferencesDao>() }, { error("unused") }, { error("unused") }, OrderedLauncher(scope), env)
        val starter = inert<DocumentSyncStarter>()
        val provider = DocumentSyncProvider { error("unused") }
        val cloud = CloudSync(AppDialogController(), strings, env, settings, adapters, inert<SyncScheduler>(), starter, provider, documents)
        fun service() = SyncSettingsServiceImpl(scope, scope, documents, cloud, adapters, SyncSettingsCategories(settings) { inert<net.bible.android.database.SyncDao>() }, provider, starter, platform, strings, env, {})
        val labels = SyncSettingsLabels.forTest()
        try {
            var controller by mutableStateOf(SyncSettingsController(service(), scope, labels, {}))
            setContent {
                val active = controller
                val state by active.state.collectAsState()
                CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                    AbTheme(darkTheme = false, disableAnimations = true) {
                        key(active) {
                            SyncSettingsScreen(state, {}, active::onSwitch, active::onListChoice, active::onTextInput,
                                active::onNavigate, active::confirmReset, active::confirmEnableDocuments,
                                active::confirmForgetCertificate, active::dismissDialog)
                        }
                    }
                }
            }
            onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-sync_adapter"))
            onNodeWithTag("settings-row-sync_adapter").performClick()
            onNodeWithText("NEXT_CLOUD").performClick()
            fun enterUrl(url: String) {
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-cloud_sync_server_url"))
                onNodeWithTag("settings-row-cloud_sync_server_url").performClick()
                onNode(hasSetTextAction()).performTextReplacement(url)
                onNodeWithText(iosStrings("en").settingsEditorApply).performClick()
            }
            for (url in listOf("http://example.org/nc", "http://127.0.0.1:8080/nc")) {
                enterUrl(url)
                onNodeWithText(labels.httpsRequiredMessage).assertIsDisplayed()
                assertFalse(credentials.containsKey("cloud_sync_server_url"))
                onNodeWithText(iosStrings("en").okay).performClick()
            }
            enterUrl("https://127.0.0.1:8080/nc")
            onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-sync_enable_bookmarks"))
            onNodeWithTag("settings-row-sync_enable_bookmarks").assertIsOn().performClick().assertIsOff()
            val recreated = service()
            assertEquals("NEXT_CLOUD", recreated.snapshot.value.adapter)
            assertEquals("https://127.0.0.1:8080/nc", recreated.snapshot.value.serverUrl)
            assertFalse(recreated.snapshot.value.categoryEnabled.getValue("sync_enable_bookmarks"))
            runOnIdle { controller = SyncSettingsController(recreated, scope, labels, {}) }
            onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-sync_adapter"))
            onNodeWithText("NEXT_CLOUD").assertIsDisplayed()
            onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-cloud_sync_server_url"))
            onNodeWithText("https://127.0.0.1:8080/nc").assertIsDisplayed()
            onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-sync_enable_bookmarks"))
            onNodeWithTag("settings-row-sync_enable_bookmarks").assertIsOff()
        } finally { scope.coroutineContext[Job]?.cancel() }
    }
}
