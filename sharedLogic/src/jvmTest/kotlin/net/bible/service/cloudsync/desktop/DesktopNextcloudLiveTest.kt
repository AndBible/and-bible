package net.bible.service.cloudsync.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.service.cloudsync.host.PlainHttpRefusedException
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.service.cloudsync.webdav.nextcloudDavUrl
import net.bible.sharedcore.webdav.DavAuthException
import net.bible.sharedcore.webdav.DavCertificateInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Opt-in real Nextcloud/OkHttp check, independent of Android's ownCloud library.
 * AND_BIBLE_NEXTCLOUD_FIXTURE names a local JSON file with server, username, email, password.
 * Use only a disposable server: this creates and deletes its own collection.
 */
class DesktopNextcloudLiveTest {
    @Test fun credentialsAndAddressRoundTrip() = runBlocking {
        val fixturePath = System.getenv("AND_BIBLE_NEXTCLOUD_FIXTURE") ?: run {
            println("DesktopNextcloudLiveTest skipped: AND_BIBLE_NEXTCLOUD_FIXTURE not set")
            return@runBlocking
        }
        val fixture = Json.parseToJsonElement(File(fixturePath).readText()).jsonObject
        fun field(name: String) = requireNotNull(fixture[name]).jsonPrimitive.content
        val server = field("server")
        val username = field("username")
        val password = field("password")
        val factory = DesktopWebDavClientFactory()
        val ui = object : WebDavSignInUi {
            override suspend fun confirmCertificate(info: DavCertificateInfo) = true
            override fun message(kind: WebDavMessage) = kind.name
        }
        fun state() = object : WebDavStateStore {
            override var propagation = Propagation.UNKNOWN
            override var certPin: CertPin? = null
        }
        for (user in listOf(username, field("email"))) {
            for (address in listOf(server.trimEnd('/'), server.trimEnd('/') + "/", nextcloudDavUrl(server, user))) {
                val config = WebDavConfig(nextcloudDavUrl(address, user), user, password, "")
                val adapter = WebDavCloudAdapter(config, state(), ui, "fixture", factory::create)
                assertTrue(adapter.signIn())
                val folder = "ticket-3537-" + System.nanoTime()
                var failure: Throwable? = null
                try {
                    val created = adapter.createNewFolder(folder)
                    assertEquals(folder, adapter.get(created.id).name)
                    assertTrue(adapter.listFiles(null).any { it.name == folder })
                } catch (error: Throwable) {
                    failure = error
                    throw error
                } finally {
                    try {
                        adapter.delete(folder)
                    } catch (cleanupError: Throwable) {
                        if (failure == null) throw cleanupError
                        failure.addSuppressed(cleanupError)
                    } finally {
                        adapter.signOut()
                    }
                }
                println("Nextcloud desktop round trip passed: email=${user != username}, address=$address")
            }
        }
        val pinnedState = state()
        val config = WebDavConfig(nextcloudDavUrl(server, username), username, password, "")
        // Pin through the real sign-in path before checking authentication failures directly.
        assertTrue(WebDavCloudAdapter(config, pinnedState, ui, "fixture", factory::create).signIn())
        assertFailsWith<DavAuthException> {
            factory.create(config.copy(username = "$username "), pinnedState).propfind("", 0)
        }
        assertFailsWith<DavAuthException> {
            factory.create(config.copy(password = password + "wrong"), pinnedState).propfind("", 0)
        }
        assertFailsWith<PlainHttpRefusedException> {
            factory.create(config.copy(serverUrl = config.serverUrl.replace("https://", "http://")), pinnedState)
        }
        println("Nextcloud desktop rejected trailing-space username, wrong password and plain HTTP as expected")
    }
}
