package net.bible.service.cloudsync.webdav

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.WebDavClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * A blank server URL (e.g. the settings row rejected an `http://` address and nothing was ever stored)
 * must fail sign-in with a message, never reach the HTTP stack: ktor resolves a relative request URL
 * against `http://localhost/`, so the app used to connect to localhost:80 (iOS NM-1 E2E, -1004).
 */
class WebDavBlankUrlSignInTest {
    private val state = object : WebDavStateStore {
        override var propagation = Propagation.UNKNOWN
        override var certPin: CertPin? = null
    }
    private val ui = object : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = false
        override fun message(kind: WebDavMessage) = kind.name
    }

    private fun signInWith(serverUrl: String): List<WebDavConfig> {
        val created = mutableListOf<WebDavConfig>()
        val adapter = WebDavCloudAdapter(
            WebDavConfig(serverUrl, "u", "p", ""), state, ui, "dev",
            clientFactory = { c, _ -> created += c; error("the HTTP client must not be created for a blank URL") },
        )
        runTest { assertFailsWith<Exception> { adapter.signIn() } }
        return created
    }

    @Test fun blankUrlFailsWithoutCreatingAClient() {
        assertEquals(emptyList(), signInWith(""))
    }

    @Test fun whitespaceUrlFailsWithoutCreatingAClient() {
        assertEquals(emptyList(), signInWith("   "))
    }

    @Test fun blankUrlReportsTheAddressMessage() = runTest {
        val adapter = WebDavCloudAdapter(WebDavConfig("", "u", "p", ""), state, ui, "dev", { _, _ -> error("no") })
        val e = assertFailsWith<Exception> { adapter.signIn() }
        assertEquals(WebDavMessage.HTTPS_REQUIRED.name, e.message)
        assertFalse(adapter.signedIn)
    }
}
