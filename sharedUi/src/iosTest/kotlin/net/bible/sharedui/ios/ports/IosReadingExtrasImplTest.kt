@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedui.app.ports.HelpDialogs
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
        var currentTable = task4Table()
        fun extras(scope: CoroutineScope, report: suspend (Throwable?) -> Unit = { causes += it }) =
            HostReadingExtrasImpl(
                helpDialogs = object : HelpDialogs { override fun show(topic: HelpTopic) { help += topic } },
                textShare = object : TextShare {
                    override fun shareText(text: String, chooserTitle: String) { shared += text to chooserTitle }
                },
                table = { currentTable },
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

    @Test fun tellFriendSharesHomepageMessageAndCurrentLocalizedChooserTitle() = runTest {
        val boundary = Boundary()
        val extras = boundary.extras(this)
        extras.runRow("tellFriend")
        runCurrent()
        val (text, title) = boundary.shared.single()
        assertEquals("en:tell_friend_title", title)
        assertContains(text, "en:tell_friend_message1(en:app_name_long)")
        assertContains(text, "en:tell_friend_message2_device")
        assertContains(text, "en:tell_friend_message4(https://andbible.org)")
        assertFalse(text.contains("play.google.com"))
        assertFalse(text.contains("apps.apple.com"))
        boundary.currentTable = task4Table("fi")
        extras.runRow("tellFriend")
        runCurrent()
        assertEquals(2, boundary.shared.size)
        assertEquals("fi:tell_friend_title", boundary.shared.last().second)
        assertContains(boundary.shared.last().first, "fi:tell_friend_message2_device")
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

    @Test fun javascriptReportUsesRealConsentAndIncludesCauseAndLogInOutgoingPayload() = runTest {
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
        runCurrent()
        assertEquals(listOf(false), confirmations)
        assertTrue(bodies.isEmpty(), "Declined consent must not send a report")
        consent = true
        extras.reportJsError()
        // report() performs actual background preparation; let structured child completion join it.
        testScheduler.advanceUntilIdle()
        // Default-dispatcher work may suspend the child beyond simulated scheduler progress.
        coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
        assertEquals(listOf(false, false), confirmations)
        assertEquals(listOf("errors.andbible@gmail.com"), recipients)
        assertContains(bodies.single(), "Error in webview-js")
        assertContains(bodies.single(), "webview")
        assertEquals(listOf(listOf("app_log.txt.gz")), attachmentNames)
    }
}
