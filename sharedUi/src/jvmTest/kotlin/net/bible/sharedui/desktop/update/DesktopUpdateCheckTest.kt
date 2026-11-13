package net.bible.sharedui.desktop.update

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bible.sharedui.settings.DesktopUpdateInterval

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopUpdateCheckTest {
    private fun settings() = DesktopDeviceSettings(Files.createTempDirectory("update-test").resolve("settings"))
    private fun release(build: Int) = """[{"tag_name":"production-$build","draft":false,"html_url":"https://example.com/$build","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""

    @Test fun unansweredConsentWaitsForAnswerAndRepeatedStartAsksOnce() = runTest {
        val prefs = settings()
        val answer = CompletableDeferred<Boolean?>()
        var questions = 0
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, DesktopChannel.PRODUCTION, backgroundScope, {},
            consent = { questions++; answer.await() })
        checker.start(); checker.start(); runCurrent()
        assertEquals(1, questions)
        assertEquals(0, requests)
        assertNull(prefs.getString("desktop_update_check"))
        answer.complete(true); runCurrent()
        assertEquals("weekly", prefs.getString("desktop_update_check"))
        assertEquals(1, requests)
    }

    @Test fun declinedConsentSurvivesUpgradeAndSendsNothing() = runTest {
        val prefs = settings()
        var requests = 0
        var questions = 0
        val first = DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, DesktopChannel.PRODUCTION, backgroundScope, {},
            consent = { questions++; false })
        first.start(); runCurrent()
        assertEquals("never", prefs.getString("desktop_update_check"))
        val upgraded = DesktopUpdateCheck(prefs, { 200000000L }, { requests++; "[]" }, 2, DesktopChannel.PRODUCTION, backgroundScope, {},
            consent = { questions++; true })
        upgraded.start(); runCurrent()
        assertEquals(1, questions)
        assertEquals(0, requests)
    }

    @Test fun attemptsAreLimitedToOncePerDayIncludingFailures() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "daily") }
        var now = 100000000L
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { now }, { requests++; throw IOException("offline") }, 1, DesktopChannel.PRODUCTION, backgroundScope, {})
        checker.start(); runCurrent()
        assertEquals(now, prefs.getLong("desktop_update_last_check", 0))
        now += 23 * 60 * 60 * 1000L
        checker.start(); runCurrent(); assertEquals(1, requests)
        now += 60 * 60 * 1000L
        checker.start(); runCurrent(); assertEquals(2, requests)
    }

    @Test fun announcesEachVersionOnceAndIgnoresErrorBodies() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "daily") }
        var now = 100000000L
        var body = release(1240)
        val announced = mutableListOf<Int>()
        val checker = DesktopUpdateCheck(prefs, { now }, { body }, 1239, DesktopChannel.PRODUCTION, backgroundScope, { announced += it.build })
        checker.start(); runCurrent()
        now += 86400000; checker.start(); runCurrent()
        body = release(1241); now += 86400000; checker.start(); runCurrent()
        body = "{\"message\":\"API rate limit exceeded\"}"; now += 86400000; checker.start(); runCurrent()
        assertEquals(listOf(1240, 1241), announced)
        assertEquals(now, prefs.getLong("desktop_update_last_check", 0))
    }

    @Test fun cancelledConsentDoesNotPersistOrFetch() = runTest {
        val prefs = settings()
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, DesktopChannel.PRODUCTION, backgroundScope, {},
            consent = { null })
        checker.start(); runCurrent()
        assertNull(prefs.getString("desktop_update_check"))
        assertEquals(0, requests)
    }

    @Test fun withdrawnQueuedAnnouncementCanBeOfferedOnNextLaunch() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "daily") }
        val dialogs = AppDialogController()
        val blocking = dialogs.post(AppDialogRequest.Message("Busy", "Other dialog", "OK"))
        var now = 100000000L
        val firstJob = Job(backgroundScope.coroutineContext[Job])
        val firstScope = CoroutineScope(backgroundScope.coroutineContext + firstJob)
        val first = DesktopUpdateCheck(prefs, { now }, { release(1240) }, 1239, DesktopChannel.PRODUCTION, firstScope, {},
            announceAndAwait = {
                dialogs.await(AppDialogRequest.Confirm("Update", "New release", "Download", "Later")) == AppDialogResult.Ok
            })
        first.start(); runCurrent()
        assertEquals(blocking, dialogs.pending.value?.id)
        firstJob.cancel(); runCurrent()
        dialogs.respond(blocking, AppDialogResult.Ok); runCurrent()
        assertNull(dialogs.pending.value)
        now += 86400000L
        val offered = mutableListOf<Int>()
        DesktopUpdateCheck(prefs, { now }, { release(1240) }, 1239, DesktopChannel.PRODUCTION, backgroundScope, { offered += it.build })
            .start()
        runCurrent()
        assertEquals(listOf(1240), offered)
    }

    @Test fun revokedConsentSuppressesPendingResult() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "daily") }
        val pending = CompletableDeferred<String>()
        val announced = mutableListOf<Int>()
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { pending.await() }, 1, DesktopChannel.PRODUCTION, backgroundScope, { announced += it.build })
        checker.start(); runCurrent()
        prefs.putString("desktop_update_check", "never")
        pending.complete(release(2)); runCurrent()
        assertEquals(emptyList(), announced)
        assertEquals(0, prefs.getInt("desktop_update_announced_build", 0))
    }

    @Test fun startReturnsWhileFetchIsSuspended() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "daily") }
        val pending = CompletableDeferred<String>()
        var entered = false
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { entered = true; pending.await() }, 1, DesktopChannel.PRODUCTION, backgroundScope, {})
        checker.start(); runCurrent()
        assertEquals(true, entered)
        checker.start(); runCurrent()
    }

    private fun ms(days: Int) = days * 24 * 60 * 60 * 1000L

    private suspend fun kotlinx.coroutines.test.TestScope.requestsAfterDays(stored: String?, days: Int): Int {
        val prefs = settings().apply { stored?.let { putString("desktop_update_check", it) } }
        var now = 100000000L
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { now }, { requests++; "[]" }, 1, DesktopChannel.PRODUCTION, backgroundScope, {})
        checker.start(); runCurrent()
        now += ms(days) - 1; checker.start(); runCurrent()
        val before = requests
        now += 1; checker.start(); runCurrent()
        assertEquals(1, before)
        return requests
    }

    @Test fun intervalGatesChecksPerChoice() = runTest {
        for ((choice, days) in listOf("daily" to 1, "weekly" to 7, "monthly" to 30)) {
            assertEquals(2, requestsAfterDays(choice, days), choice)
        }
    }

    @Test fun neverChecksNothing() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "never") }
        var requests = 0; var questions = 0
        DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, DesktopChannel.PRODUCTION, backgroundScope, {},
            consent = { questions++; true }).start()
        runCurrent()
        assertEquals(0, requests); assertEquals(0, questions)
    }

    @Test fun legacyYesMigratesToWeeklyAndNoToNever() = runTest {
        val yes = settings().apply { putString("desktop_update_check", "yes") }
        assertEquals(2, requestsAfterDaysWith(yes, 7))
        assertEquals("weekly", yes.getString("desktop_update_check"))
        val no = settings().apply { putString("desktop_update_check", "no") }
        assertEquals(0, requestsAfterDaysWith(no, 7))
        assertEquals("never", no.getString("desktop_update_check"))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.requestsAfterDaysWith(prefs: DesktopDeviceSettings, days: Int): Int {
        var now = 100000000L
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { now }, { requests++; "[]" }, 1, DesktopChannel.PRODUCTION, backgroundScope, {})
        checker.start(); runCurrent()
        now += ms(days); checker.start(); runCurrent()
        return requests
    }

    @Test fun snoozeHoldsAnnouncementsUntilItExpiresThenChecksAtOnce() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "monthly") }
        var now = 100000000L
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { now }, { requests++; release(1240) }, 1239, DesktopChannel.PRODUCTION, backgroundScope, {})
        prefs.putLong("desktop_update_last_check", now)
        prefs.snoozeUntil(now + ms(1))
        checker.start(); runCurrent(); assertEquals(0, requests)
        now += ms(1) - 1; checker.start(); runCurrent(); assertEquals(0, requests)
        now += 1; checker.start(); runCurrent(); assertEquals(1, requests)
    }

    @Test fun skippedVersionIsNotOfferedAgainButANewerOneIs() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "daily") }
        var now = 100000000L
        var body = release(1240)
        val offered = mutableListOf<Int>()
        val checker = DesktopUpdateCheck(prefs, { now }, { body }, 1239, DesktopChannel.PRODUCTION, backgroundScope, {},
            announceAndAwait = { offered += it.build; true })
        checker.start(); runCurrent()
        now += ms(1); checker.start(); runCurrent()
        body = release(1241); now += ms(1); checker.start(); runCurrent()
        assertEquals(listOf(1240, 1241), offered)
    }

    private fun mixed() = """[
        {"tag_name":"beta-1245","draft":false,"html_url":"https://example.com/b","assets":[{"name":"andbible-desktop-linux.deb"}]},
        {"tag_name":"production-1240","draft":false,"html_url":"https://example.com/p","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""

    private suspend fun kotlinx.coroutines.test.TestScope.offeredTag(channel: DesktopChannel, offerBeta: String?): String? {
        val prefs = settings().apply { putString("desktop_update_check", "daily"); offerBeta?.let { putString("desktop_update_offer_beta", it) } }
        var tag: String? = null
        DesktopUpdateCheck(prefs, { 100000000L }, { mixed() }, 1230, channel, backgroundScope, { tag = it.tag }).start()
        runCurrent()
        return tag
    }

    @Test fun storedOfferBetaChoiceDrivesTheCheck() = runTest {
        assertEquals("production-1240", offeredTag(DesktopChannel.PRODUCTION, null))
        assertEquals("beta-1245", offeredTag(DesktopChannel.PRODUCTION, "yes"))
        assertEquals("beta-1245", offeredTag(DesktopChannel.BETA, null))
        assertEquals("production-1240", offeredTag(DesktopChannel.BETA, "no"))
    }
}
