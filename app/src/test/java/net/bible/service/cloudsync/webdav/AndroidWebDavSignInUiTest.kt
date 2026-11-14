package net.bible.service.cloudsync.webdav

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.parseHtmlRuns
import net.bible.sharedcore.webdav.DavCertificateInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #4100: the confirm dialog renders its message as HTML, so the plain-text newlines between Subject, Issuer, Valid and
 * SHA-256 must be turned into line breaks (as the shared host prompt does) or Android shows one run-together paragraph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class AndroidWebDavSignInUiTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun certificateDetailsRenderOnSeparateLines(): Unit = runBlocking {
        val dialogs = AppDialogController()
        val hadKoin = GlobalContext.getOrNull() != null
        if (hadKoin) stopKoin()
        startKoin { modules(module { single { dialogs } }) }
        try {
            val ui = AndroidWebDavSignInUi(ApplicationProvider.getApplicationContext())
            val info = DavCertificateInfo("10.0.2.2", "AABB", "O=AndBible test server,CN=10.0.2.2 <x>", "CN=ca & co", 0L, 1L)
            val answer = async { ui.confirmCertificate(info) }
            val shown = withTimeout(10_000) {
                var s = dialogs.pending.value
                while (s == null) { kotlinx.coroutines.yield(); s = dialogs.pending.value }
                s
            }
            val prompt = shown.request as AppDialogRequest.Confirm
            val text = parseHtmlRuns(prompt.message!!).joinToString("") { it.text }
            val lines = text.lines()
            assertTrue(text, lines.any { it.startsWith("Subject: O=AndBible test server,CN=10.0.2.2 <x>") })
            assertTrue(text, lines.any { it.startsWith("Issuer: CN=ca & co") })
            assertTrue(text, lines.any { it.startsWith("Valid: ") })
            assertTrue(text, lines.any { it.startsWith("SHA-256: AA:BB") })
            dialogs.respond(shown.id, AppDialogResult.Cancel)
            answer.await(); Unit
        } finally {
            dialogs.cancelAll()
            stopKoin()
        }
    }
}
