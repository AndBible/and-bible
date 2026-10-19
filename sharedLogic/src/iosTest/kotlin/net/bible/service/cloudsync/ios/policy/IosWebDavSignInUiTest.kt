package net.bible.service.cloudsync.ios

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.DavCertificateInfo
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosWebDavSignInUiTest {
    @Test fun certificatePromptShowsIssuerAndDates() = runTest {
        val dialogs = AppDialogController()
        val ui = IosWebDavCertificatePrompt({ dialogs }, StubCoreStrings()) { "date-$it" }
        val answer = async { ui.confirmCertificate(DavCertificateInfo("dav.example", "AABBCC", "Server CN", "Real Issuer", 1000, 2000)) }
        try {
            runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val prompt = assertIs<AppDialogRequest.Confirm>(shown.request)
            assertEquals("webDavUntrustedCertificateTitle", prompt.title)
            assertEquals("dav.example|Server CN|Real Issuer|date-1000|date-2000|AA:BB:CC", prompt.message)
            assertEquals("webDavTrustCertificate", prompt.confirmText)
            assertEquals("cancel", prompt.dismissText)
            dialogs.respond(shown.id, AppDialogResult.Ok)
            assertTrue(answer.await())
        } finally { dialogs.cancelAll(); answer.cancel() }
    }

    @Test fun decliningCertificateDoesNotGrantTrust() = runTest {
        val dialogs = AppDialogController()
        val ui = IosWebDavCertificatePrompt({ dialogs }, StubCoreStrings()) { it.toString() }
        val answer = async { ui.confirmCertificate(DavCertificateInfo("host", "AA", "subject", "issuer", 1, 2)) }
        try {
            runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            dialogs.respond(shown.id, AppDialogResult.Cancel)
            assertFalse(answer.await())
        } finally { dialogs.cancelAll(); answer.cancel() }
    }

    @Test fun allWebDavMessagesAreLocalized() {
        val ui = IosWebDavCertificatePrompt({ error("messages cannot present UI") }, StubCoreStrings()) { it.toString() }
        assertEquals(listOf("webDavWrongCredentials", "webDavNotAFolder", "webDavCertificateChanged", "webDavStorageFull"),
            listOf(WebDavMessage.WRONG_CREDENTIALS, WebDavMessage.NOT_A_FOLDER, WebDavMessage.CERTIFICATE_CHANGED, WebDavMessage.STORAGE_FULL).map(ui::message))
    }
}
