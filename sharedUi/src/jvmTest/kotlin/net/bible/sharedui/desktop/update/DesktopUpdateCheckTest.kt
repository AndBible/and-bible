package net.bible.sharedui.desktop.update

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopUpdateCheckTest {
    private fun settings() = DesktopDeviceSettings(Files.createTempDirectory("update-test").resolve("settings"))
    private fun release(build: Int) = """[{"tag_name":"production-$build","draft":false,"html_url":"https://example.com/$build","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""

    @Test fun unansweredConsentWaitsForAnswerAndRepeatedStartAsksOnce() = runTest {
        val prefs = settings()
        val answer = CompletableDeferred<Boolean?>()
        var questions = 0
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, true, backgroundScope, {},
            consent = { questions++; answer.await() })
        checker.start(); checker.start(); runCurrent()
        assertEquals(1, questions)
        assertEquals(0, requests)
        assertNull(prefs.getString("desktop_update_check"))
        answer.complete(true); runCurrent()
        assertEquals("yes", prefs.getString("desktop_update_check"))
        assertEquals(1, requests)
    }

    @Test fun declinedConsentSurvivesUpgradeAndSendsNothing() = runTest {
        val prefs = settings()
        var requests = 0
        var questions = 0
        val first = DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, true, backgroundScope, {},
            consent = { questions++; false })
        first.start(); runCurrent()
        assertEquals("no", prefs.getString("desktop_update_check"))
        val upgraded = DesktopUpdateCheck(prefs, { 200000000L }, { requests++; "[]" }, 2, true, backgroundScope, {},
            consent = { questions++; true })
        upgraded.start(); runCurrent()
        assertEquals(1, questions)
        assertEquals(0, requests)
    }

    @Test fun attemptsAreLimitedToOncePerDayIncludingFailures() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "yes") }
        var now = 100000000L
        var requests = 0
        val checker = DesktopUpdateCheck(prefs, { now }, { requests++; throw IOException("offline") }, 1, true, backgroundScope, {})
        checker.start(); runCurrent()
        assertEquals(now, prefs.getLong("desktop_update_last_check", 0))
        now += 23 * 60 * 60 * 1000L
        checker.start(); runCurrent(); assertEquals(1, requests)
        now += 60 * 60 * 1000L
        checker.start(); runCurrent(); assertEquals(2, requests)
    }

    @Test fun announcesEachVersionOnceAndIgnoresErrorBodies() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "yes") }
        var now = 100000000L
        var body = release(1240)
        val announced = mutableListOf<Int>()
        val checker = DesktopUpdateCheck(prefs, { now }, { body }, 1239, true, backgroundScope, { announced += it.build })
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
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { requests++; "[]" }, 1, true, backgroundScope, {},
            consent = { null })
        checker.start(); runCurrent()
        assertNull(prefs.getString("desktop_update_check"))
        assertEquals(0, requests)
    }

    @Test fun revokedConsentSuppressesPendingResult() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "yes") }
        val pending = CompletableDeferred<String>()
        val announced = mutableListOf<Int>()
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { pending.await() }, 1, true, backgroundScope, { announced += it.build })
        checker.start(); runCurrent()
        prefs.putString("desktop_update_check", "no")
        pending.complete(release(2)); runCurrent()
        assertEquals(emptyList(), announced)
        assertEquals(0, prefs.getInt("desktop_update_announced_build", 0))
    }

    @Test fun startReturnsWhileFetchIsSuspended() = runTest {
        val prefs = settings().apply { putString("desktop_update_check", "yes") }
        val pending = CompletableDeferred<String>()
        var entered = false
        val checker = DesktopUpdateCheck(prefs, { 100000000L }, { entered = true; pending.await() }, 1, true, backgroundScope, {})
        checker.start(); runCurrent()
        assertEquals(true, entered)
        checker.start(); runCurrent()
    }
}
