package net.bible.service.cloudsync

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.webdav.DavCertificateInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncServerUrlTest {
    private class ClientReached : Exception()
    private val state = object : WebDavStateStore {
        override var propagation = Propagation.UNKNOWN
        override var certPin: CertPin? = null
    }
    private val ui = object : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = false
        override fun message(kind: WebDavMessage) = kind.name
    }

    @Test fun normalizesServerRootsAndSubpathInstallsBeforeCreatingClient() = runTest {
        for ((input, expected) in listOf(
            "https://host:8080/" to "https://host:8080",
            "  https://host/nextcloud///\n" to "https://host/nextcloud",
            "https://host/remote.php/dav/files/me/" to "https://host/remote.php/dav/files/me",
            "https://host/a%2Fb" to "https://host/a%2Fb",
            "https://host" to "https://host",
        )) {
            var receivedUrl: String? = null
            val adapter = WebDavCloudAdapter(WebDavConfig(input, "u", "p", ""), state, ui, "dev", { c, _ ->
                receivedUrl = c.serverUrl
                throw ClientReached()
            })
            assertFailsWith<ClientReached> { adapter.signIn() }
            assertEquals(expected, receivedUrl, input)
        }
    }
}
