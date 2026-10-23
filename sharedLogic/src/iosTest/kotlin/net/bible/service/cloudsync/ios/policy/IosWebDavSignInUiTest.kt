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
    @Test fun certificateIdentifiersRenderLiterallyWithoutMarkupOrLinks() = runTest {
        val dialogs = AppDialogController()
        val strings = StubCoreStrings()
        val host = "dav.example<a href=\"https://attacker.invalid\">link</a>"
        val subject = "CN=<b>not bold</b>\n<!--still identity-->&amp; &lt;name&gt;"
        val issuer = "O=<i>not italic</i><big>not big</big><small>not small</small>"
        val ui = IosWebDavCertificatePrompt({ dialogs }, strings) { "date-$it" }
        val answer = async { ui.confirmCertificate(DavCertificateInfo(host, "AABB", subject, issuer, 1, 2)) }
        try {
            runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val prompt = assertIs<AppDialogRequest.Confirm>(shown.request)
            val runs = net.bible.sharedcore.ui.dialog.parseHtmlRuns(assertNotNull(prompt.message))
            val expected = strings.webDavUntrustedCertificateMessageDevice(host, subject, issuer, "date-1", "date-2", "AA:BB")
            assertEquals(expected, runs.joinToString("") { it.text }, "Trust identifiers and line breaks must survive final HTML rendering literally")
            assertTrue(runs.all { it.href == null && !it.bold && !it.italic && it.size == net.bible.sharedcore.ui.dialog.HtmlRun.Size.Normal },
                "Certificate fields must not introduce links or styles")
        } finally { dialogs.cancelAll(); answer.cancel() }
    }

    @Test fun certificatePromptShowsIssuerAndDates() = runTest {
        val dialogs = AppDialogController()
        val ui = IosWebDavCertificatePrompt({ dialogs }, StubCoreStrings()) { "date-$it" }
        val answer = async { ui.confirmCertificate(DavCertificateInfo("dav.example", "AABBCC", "Server CN", "Real Issuer", 1000, 2000)) }
        try {
            runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val prompt = assertIs<AppDialogRequest.Confirm>(shown.request)
            assertEquals("webDavUntrustedCertificateTitle", prompt.title)
            val shownText = net.bible.sharedcore.ui.dialog.parseHtmlRuns(assertNotNull(prompt.message)).joinToString("") { it.text }
            assertTrue(listOf("dav.example", "Server CN", "Real Issuer", "date-1000", "date-2000", "AA:BB:CC").all { it in shownText }, shownText)
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
