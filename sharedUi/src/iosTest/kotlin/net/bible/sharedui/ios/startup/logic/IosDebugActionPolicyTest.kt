package net.bible.sharedui.ios.startup

import net.bible.sharedcore.platform.UserNotifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IosDebugActionPolicyTest {
    @Test fun debugCrashSchedulesFatalExceptionAfterTwoSeconds() {
        var scheduled: (() -> Unit)? = null
        var delay: Long? = null
        val policy = IosDebugActionPolicy({ true }, silentNotifier(), { "localized error" }) { ms, action ->
            delay = ms
            scheduled = action
        }
        policy.crashAppDelayed()
        assertEquals(2000L, delay)
        // The native scheduler owns this callback; it must throw, not log or swallow the failure.
        assertFailsWith<IllegalStateException> { assertNotNull(scheduled).invoke() }
    }

    @Test fun releaseRejectsBothActionsBeforeReadingStringsOrScheduling() {
        var shown: String? = null
        val policy = IosDebugActionPolicy({ false }, object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) = error("not an info action")
            override fun showError(message: String, cause: Throwable?) { shown = message }
        }, { error("Release must not resolve debug strings") }) { _, _ -> error("Release scheduled a fatal action") }
        policy.crashAppDelayed()
        policy.showTestError()
        assertNull(shown)
    }

    @Test fun testErrorUsesLiveLocalizedMessageAndErrorRoute() {
        val received = mutableListOf<String>()
        var localized = "translated first"
        val policy = IosDebugActionPolicy({ true }, object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) = error("Error must not become an info toast")
            override fun showError(message: String, cause: Throwable?) { received += message }
        }, { localized }) { _, _ -> error("Showing an error must not schedule a crash") }
        policy.showTestError()
        localized = "translated second"
        policy.showTestError()
        assertEquals(listOf("translated first", "translated second"), received)
    }

    private fun silentNotifier() = object : UserNotifier {
        override fun showInfo(message: String, long: Boolean) = error("Crash must not show info")
        override fun showError(message: String, cause: Throwable?) = error("Crash must not become a caught error")
    }
}
