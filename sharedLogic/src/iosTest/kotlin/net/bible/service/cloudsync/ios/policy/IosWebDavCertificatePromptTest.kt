package net.bible.service.cloudsync.ios

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.parseHtmlRuns
import net.bible.sharedcore.webdav.DavCertificateInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The iOS certificate prompt must use the device-neutral wording. The stub mirrors the English resource text; the
 * resource itself is checked in `KeyedStringsTest` (sharedUi jvmTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosWebDavCertificatePromptTest {
    @Test fun messageDoesNotNameAndroid() = runTest {
        val dialogs = AppDialogController()
        val ui = IosWebDavCertificatePrompt({ dialogs }, StubCoreStrings()) { "date-$it" }
        val answer = async {
            ui.confirmCertificate(DavCertificateInfo("dav.example", "AABBCC", "CN=Server", "CN=Real Issuer", 1000, 2000))
        }
        try {
            runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val prompt = assertIs<AppDialogRequest.Confirm>(shown.request)
            val text = parseHtmlRuns(assertNotNull(prompt.message)).joinToString("") { it.text }
            assertTrue("this device" in text, text)
            assertFalse("Android" in text, text)
            listOf("dav.example", "CN=Server", "CN=Real Issuer", "date-1000", "date-2000", "AA:BB:CC").forEach {
                assertTrue(it in text, "Missing $it in: $text")
            }
            assertTrue("SHA-256: AA:BB:CC" in text, text)
            dialogs.respond(shown.id, AppDialogResult.Cancel)
        } finally { dialogs.cancelAll(); answer.cancel() }
    }
}
