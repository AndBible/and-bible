@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import net.bible.sharedui.assembly.awaitStartupOnMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.ios.report.IosBugReport
import kotlin.test.*
import net.bible.sharedui.host.ports.HostReadingExtrasImpl

class IosReadingExtrasImplTest {
    private class Boundary {
        val help = mutableListOf<HelpTopic>()
        val shared = mutableListOf<Pair<String, String>>()
        val causes = mutableListOf<Throwable?>()
        val asked = mutableListOf<AppDialogRequest>()
        var answer: AppDialogResult = AppDialogResult.Ok
        var language = "en"
        fun extras(scope: CoroutineScope, report: suspend (Throwable?) -> Unit = { causes += it }) =
            HostReadingExtrasImpl(
                helpDialogs = object : HelpDialogs { override fun show(topic: HelpTopic) { help += topic } },
                textShare = object : TextShare {
                    override fun shareText(text: String, chooserTitle: String) { shared += text to chooserTitle }
                },
                strings = { iosStrings(language) },
                ask = { request -> asked += request; answer },
                scope = scope,
                report = report,
            )
    }

    @Test fun offersHelpLicenceBugReportTellFriendOnly() = runTest {
        assertEquals(setOf("helpButton", "appLicence", "bugReport", "tellFriend"), Boundary().extras(this).offeredRows)
    }

    @Test fun runRowDispatchesHelpAndLicenceWithoutSharingOrReporting() = runTest {
        val boundary = Boundary()
        val extras = boundary.extras(this)
        extras.runRow("helpButton")
        extras.runRow("appLicence")
        runCurrent()
        assertEquals(listOf(HelpTopic.HELP_AND_TIPS, HelpTopic.LICENCE), boundary.help)
        assertTrue(boundary.shared.isEmpty())
        assertTrue(boundary.causes.isEmpty())
    }

    @Test fun tellFriendShowsQrDialogAndClosingSharesNothing() = runTest {
        val boundary = Boundary()
        boundary.answer = AppDialogResult.Cancel
        boundary.extras(this).runRow("tellFriend")
        runCurrent()
        val dialog = assertIs<AppDialogRequest.Notice>(boundary.asked.single())
        assertTrue(dialog.blocks.contains(AppDialogRequest.NoticeBlock.QrCode))
        assertTrue(boundary.shared.isEmpty(), "Close shares no text")
    }

    @Test fun tellFriendSharesNeutralHomepageMessageInCurrentLanguage() = runTest {
        val boundary = Boundary()
        val extras = boundary.extras(this)
        extras.runRow("tellFriend")
        runCurrent()
        val (text, title) = boundary.shared.single()
        assertEquals(iosStrings("en").tellFriendTitle, title)
        assertContains(text, "https://andbible.org")
        assertFalse(text.contains("play.google.com"))
        assertFalse(text.contains("apps.apple.com"))
        assertFalse(text.contains("Android", ignoreCase = true))
        boundary.language = "fi"
        extras.runRow("tellFriend")
        runCurrent()
        assertEquals(iosStrings("fi").tellFriendTitle, boundary.shared.last().second)
        assertTrue(boundary.help.isEmpty())
        assertTrue(boundary.causes.isEmpty())
    }

    @Test fun manualReportHasNoCauseButJavascriptReportRetainsDiagnostic() = runTest {
        val boundary = Boundary()
        val extras = boundary.extras(this)
        extras.runRow("bugReport")
        extras.reportJsError()
        runCurrent()
        assertEquals(2, boundary.causes.size)
        assertNull(boundary.causes.first())
        val cause = assertNotNull(boundary.causes.last())
        assertEquals("Error in webview-js", cause.message)
        assertContains(cause.stackTraceToString(), "webview")
        assertTrue(boundary.help.isEmpty())
        assertTrue(boundary.shared.isEmpty())
    }

    @Test fun unknownAndUnsupportedStoreRowsAreIgnored() = runTest {
        val boundary = Boundary()
        val extras = boundary.extras(this)
        listOf("unknown", "", "rateButton", "buyDevelopment", "backup", "cloudSync", "studyPads")
            .forEach(extras::runRow)
        runCurrent()
        assertTrue(boundary.help.isEmpty())
        assertTrue(boundary.shared.isEmpty())
        assertTrue(boundary.causes.isEmpty())
    }

    @Test fun javascriptReportUsesRealConsentAndIncludesCauseAndLogInOutgoingPayload() = awaitStartupOnMain { coroutineScope {
        val boundary = Boundary()
        var consent = false
        val confirmations = mutableListOf<Boolean>()
        val recipients = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val attachmentNames = mutableListOf<List<String>>()
        val report = IosBugReport(
            table = task4Table(),
            confirm = { ai -> confirmations += ai; consent },
            send = { recipient, subject, body, attachments ->
                recipients += recipient
                assertTrue(subject.startsWith("Bug Report v"))
                bodies += body
                attachmentNames += attachments.map { it.name }
                assertEquals("application/gzip", attachments.single().mime)
                assertTrue(attachments.single().bytes.isNotEmpty())
            },
            failure = { throw AssertionError("Real report preparation failed", it) },
        )
        val extras = boundary.extras(this, report = { cause -> report.report(cause) })
        extras.reportJsError()
        coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(listOf(false), confirmations)
        assertTrue(bodies.isEmpty(), "Declined consent must not send a report")
        consent = true
        extras.reportJsError()
        // Pump real Main outside this scope while report preparation returns from Default.
        coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(listOf(false, false), confirmations)
        assertEquals(listOf("errors.andbible@gmail.com"), recipients)
        assertContains(bodies.single(), "Error in webview-js")
        assertContains(bodies.single(), "webview")
        assertEquals(listOf(listOf("app_log.txt.gz")), attachmentNames)
    } }
}
