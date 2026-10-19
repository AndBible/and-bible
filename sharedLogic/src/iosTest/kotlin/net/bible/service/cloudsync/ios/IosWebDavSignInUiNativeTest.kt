package net.bible.service.cloudsync.ios

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.DavCertificateInfo
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosWebDavSignInUiNativeTest {
    @Test fun nativeWrapperFormatsActualCertificateValidity() = runTest {
        val dialogs = AppDialogController()
        val ui = IosWebDavSignInUi({ dialogs }, StubCoreStrings())
        val answer = async { ui.confirmCertificate(DavCertificateInfo("dav.example", "AABB", "subject", "issuer", 1433415878000, 2064567878000)) }
        try {
            runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val prompt = assertIs<AppDialogRequest.Confirm>(shown.request)
            val formatter = NSDateFormatter().apply { dateStyle = NSDateFormatterMediumStyle; timeStyle = NSDateFormatterNoStyle }
            val from = formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(1433415878.0))
            val until = formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(2064567878.0))
            assertEquals("dav.example|subject|issuer|$from|$until|AA:BB", prompt.message)
            dialogs.respond(shown.id, AppDialogResult.Ok)
            assertTrue(answer.await())
        } finally { dialogs.cancelAll(); answer.cancel() }
    }
}
