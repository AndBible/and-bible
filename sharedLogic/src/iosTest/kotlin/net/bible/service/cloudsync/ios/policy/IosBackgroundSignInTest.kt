package net.bible.service.cloudsync.ios

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.sharedcore.webdav.DavCertificateInfo
import kotlin.test.*
import net.bible.service.cloudsync.host.IosBackgroundSignInUi

class IosBackgroundSignInTest {
    @Test fun backgroundSignInNeverShowsADialog() = runTest {
        var dialogs = 0
        val foreground = object : WebDavSignInUi {
            override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean { dialogs++; return true }
            override fun message(kind: WebDavMessage) = "localized"
        }
        val background = IosBackgroundSignInUi(foreground)
        assertFalse(background.confirmCertificate(DavCertificateInfo("host", "AA", "subject", "issuer", 1, 2)))
        assertEquals(0, dialogs)
        assertEquals("localized", background.message(WebDavMessage.WRONG_CREDENTIALS))
    }
}
