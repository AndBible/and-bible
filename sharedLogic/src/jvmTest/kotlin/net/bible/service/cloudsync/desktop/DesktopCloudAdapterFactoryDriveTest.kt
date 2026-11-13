package net.bible.service.cloudsync.desktop

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.desktop.drive.desktopClient
import net.bible.service.cloudsync.desktop.drive.googleDriveSignInDonePage
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.oauth.GoogleDriveSecretKeys
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClient
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients
import net.bible.service.cloudsync.host.HostSyncStrings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.desktop.secrets.SessionMemoryBackend
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.ui.dialog.AppDialogController
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCloudAdapterFactoryDriveTest {
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

    private fun <T> withFactory(
        client: GoogleOAuthClient?,
        openBrowser: suspend (String) -> Boolean = { false },
        block: (CloudAdapterFactory, SecretStore) -> T,
    ): T = withKoin(client, openBrowser) { koin -> block(koin.get(), koin.get()) }

    private fun <T> withKoin(
        client: GoogleOAuthClient?,
        openBrowser: suspend (String) -> Boolean = { false },
        block: (org.koin.core.Koin) -> T,
    ): T {
        val app = koinApplication(createEagerInstances = false) {
            modules(
                module {
                    single<DeviceLocalPrefs> { MapPrefs() }
                    single<AppEnvironment> { inert() }
                    single<CoreStrings> { inert() }
                    single { AppDialogController() }
                    single { HostSyncStrings({ it }, { "" }, { "" }, { "" }, { "" }, { "Signed in <b>" }) }
                },
                desktopSyncModule(
                    { SessionMemoryBackend() }, openBrowser, { client },
                    { MockEngine { respondError(HttpStatusCode.InternalServerError) } },
                ),
            )
        }
        try {
            return block(app.koin)
        } finally {
            app.close()
        }
    }

    @Test fun driveHttpClientIsNotServedUnqualified() {
        withKoin(GoogleOAuthClient("id", "secret")) { koin ->
            koin.get<CloudAdapterFactory>().create("GOOGLE_DRIVE")
            assertNotNull(koin.get<io.ktor.client.HttpClient>(net.bible.service.cloudsync.drive.GOOGLE_DRIVE_HTTP))
            assertNull(koin.getOrNull<io.ktor.client.HttpClient>())
        }
    }

    @Test fun withoutClientDriveIsAbsent(): Unit = withFactory(null) { f, _ ->
        assertEquals(listOf("NEXT_CLOUD", "WEBDAV"), f.available)
    }

    @Test fun withClientDriveIsOfferedLastAndCreated(): Unit = withFactory(GoogleOAuthClient("id", "secret")) { f, _ ->
        assertEquals(listOf("NEXT_CLOUD", "WEBDAV", "GOOGLE_DRIVE"), f.available)
        assertIs<GoogleDriveRestAdapter>(f.create("GOOGLE_DRIVE"))
        assertIs<GoogleDriveRestAdapter>(f.createForBackground("GOOGLE_DRIVE")); Unit
    }

    @Test fun backgroundSignInWithEmptyStoreIsFalseWithoutOpeningBrowser() {
        val opened = AtomicInteger()
        withFactory(GoogleOAuthClient("id", "secret"), { opened.incrementAndGet(); true }) { f, secrets ->
            val adapter = f.createForBackground("GOOGLE_DRIVE")
            assertFalse(adapter.signedIn)
            assertFalse(runBlocking { adapter.signIn() })
            assertEquals(0, opened.get())
            assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        }
    }

    @Test fun refreshTokenCountsAsSignedInAndLivesInSecretStore(): Unit = withFactory(GoogleOAuthClient("id", "secret")) { f, secrets ->
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "rt")
        assertTrue(f.create("GOOGLE_DRIVE").signedIn)
    }

    @Test fun refreshTokenKeyIsSecretAndRoutedToSecretStore() {
        assertTrue(GoogleDriveSecretKeys.REFRESH_TOKEN in DESKTOP_SECRET_CREDENTIAL_KEYS)
        val prefs = MapPrefs()
        val secrets = object : SecretStore {
            val map = HashMap<String, String>()
            override fun get(key: String) = map[key]
            override fun put(key: String, value: String) { map[key] = value }
            override fun remove(key: String) { map.remove(key) }
        }
        val platform = DesktopSyncSettingsPlatform(prefs, secrets) { "" }
        platform.setCredential(GoogleDriveSecretKeys.REFRESH_TOKEN, "rt")
        assertEquals("rt", secrets.map[GoogleDriveSecretKeys.REFRESH_TOKEN])
        assertTrue(prefs.map.isEmpty())
        assertEquals("rt", platform.credential(GoogleDriveSecretKeys.REFRESH_TOKEN))
    }

    @Test fun desktopClientHonoursJvmProperties() {
        assertNull(GoogleOAuthClients.desktopClient { null })
        assertNull(GoogleOAuthClients.desktopClient { "  " })
        val c = assertNotNull(GoogleOAuthClients.desktopClient { if (it.endsWith("Id")) "cid" else "sec" })
        assertEquals(GoogleOAuthClient("cid", "sec"), c)
        assertNull(GoogleOAuthClients.desktopClient { if (it.endsWith("Id")) "cid" else null }!!.clientSecret)
    }

    @Test fun successPageEscapesHtml() {
        val page = googleDriveSignInDonePage("a <b> & \"c\"")
        assertTrue("a &lt;b&gt; &amp; &quot;c&quot;" in page)
        assertFalse("<b>" in page)
    }
}
