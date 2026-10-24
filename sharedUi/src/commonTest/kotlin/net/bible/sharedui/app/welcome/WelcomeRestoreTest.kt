package net.bible.sharedui.app.welcome

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.files.PickedSource
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WelcomeRestoreTest {
    private val source = PickedSource("file:///backup.zip", "backup.zip", null)

    @Test fun brokenBackupIsReportedWithoutReturningToReading() = runTest {
        val failure = IOException("corrupt zip")
        val events = mutableListOf<String>()
        restoreWelcomeBackup(
            pick = { source }, restore = { throw failure },
            reportError = { message, cause -> assertSame(failure, cause); events += message },
            fallbackError = { "restore failed" }, afterRestore = { events += "return gate" },
        )
        assertEquals(listOf("corrupt zip"), events)
    }

    @Test fun cancelledRestorePropagatesWithoutReportingOrReturning() = runTest {
        val cancellation = CancellationException("cancelled")
        val events = mutableListOf<String>()
        val thrown = assertFailsWith<CancellationException> {
            restoreWelcomeBackup(
                pick = { source }, restore = { throw cancellation },
                reportError = { _, _ -> events += "error" }, fallbackError = { "restore failed" },
                afterRestore = { events += "return gate" },
            )
        }
        assertSame(cancellation, thrown)
        assertEquals(emptyList(), events)
    }

    @Test fun pickerFailureIsReportedWithTranslatedFallback() = runTest {
        val failure = IOException()
        val events = mutableListOf<String>()
        restoreWelcomeBackup(
            pick = { throw failure }, restore = { error("must not restore") },
            reportError = { message, cause -> assertSame(failure, cause); events += message },
            fallbackError = { "translated fallback" }, afterRestore = { error("must not return") },
        )
        assertEquals(listOf("translated fallback"), events)
    }

    @Test fun onlySuccessfulRestoreCallsReturnGate() = runTest {
        val events = mutableListOf<String>()
        for (result in listOf(false, true)) {
            restoreWelcomeBackup(
                pick = { source }, restore = { assertSame(source, it); result },
                reportError = { _, _ -> error("unexpected error") }, fallbackError = { "restore failed" },
                afterRestore = { events += "return gate" },
            )
        }
        assertEquals(listOf("return gate"), events)
    }
}
