package net.bible.sharedui.host.startup

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.bible.service.db.CorruptDatabase
import net.bible.service.db.CorruptDatabasesException
import net.bible.sharedui.assembly.HostAppStartupState
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import net.bible.service.db.CorruptDatabaseResetException
import androidx.compose.ui.test.assertIsEnabled
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/** #4112: the desktop/iOS damaged-database screen and the startup retry behind it. */
@OptIn(ExperimentalTestApi::class)
class CorruptDatabaseRecoveryTest {
    private val damaged = CorruptDatabasesException(listOf(CorruptDatabase("bookmarks.sqlite3", "/data/bookmarks.sqlite3")))

    @Test
    fun theScreenNamesTheDamagedFileAndResetsOnlyWhenChosenThenRetries() = runComposeUiTest {
        val resets = mutableListOf<List<CorruptDatabase>>()
        var retries = 0
        var reports = 0
        setContent {
            CorruptDatabaseRecovery(
                damaged, dark = false, strings = iosStrings("en"),
                onReport = { reports++ },
                onRetry = { retries++ },
                reset = { resets += it },
            ) { _ -> error("backup not opened") }
        }
        onNodeWithTag("corrupt-database").assertExists()
        onNodeWithText("bookmarks.sqlite3", substring = true).assertExists()
        runOnIdle { assertEquals(0, resets.size, "nothing is reset before the user chooses it") }

        onNodeWithTag("corrupt-database-report").performClick()
        waitForIdle()
        runOnIdle {
            assertEquals(1, reports)
            assertEquals(0, retries, "a report does not retry the startup")
        }

        onNodeWithTag("corrupt-database-reset").performClick()
        waitUntil(timeoutMillis = 5_000) { retries == 1 }
        runOnIdle { assertEquals(listOf(damaged.databases), resets) }
    }

    @Test
    fun aFailedResetDoesNotRetryAndTheScreenStaysUsable() = runComposeUiTest {
        var retries = 0
        var attempts = 0
        setContent {
            CorruptDatabaseRecovery(
                damaged, dark = true, strings = iosStrings("en"),
                onReport = {}, onRetry = { retries++ },
                reset = { attempts++; throw CorruptDatabaseResetException(it.single(), okio.IOException("read-only file system")) },
            ) { _ -> error("backup not opened") }
        }
        onNodeWithTag("corrupt-database-reset-failed").assertDoesNotExist()
        onNodeWithTag("corrupt-database-reset").performClick()
        waitUntil(timeoutMillis = 5_000) { attempts == 1 }
        waitForIdle()
        runOnIdle { assertEquals(0, retries) }
        onNodeWithTag("corrupt-database").assertExists()
        // #4112 review: the failure is told, not silent; the choices stay usable.
        onNodeWithTag("corrupt-database-reset-failed").assertExists()
        onNodeWithText(iosStrings("en").corruptDatabaseResetFailed("bookmarks.sqlite3")).assertExists()
        onNodeWithTag("corrupt-database-reset").assertIsEnabled()
    }

    @Test
    fun damagedDataFoundAfterTheStartShowsTheRecoveryAndARetryStartsAgain() = runTest(timeout = 30.seconds) {
        var opens = 0
        val state = HostAppStartupState(open = { opens++ })
        val previousRoute = net.bible.service.db.DamagedDataRecovery.route
        try {
            state.registerDamagedDataRoute()
            state.launch(this)
            assertEquals(StartupPhase.Ready, state.phase.first { it !is StartupPhase.Starting })

            val fallback = net.bible.service.db.DamagedDataRecovery.orFallback(emptyList<String>()) { throw damaged }

            assertEquals(emptyList(), fallback)
            assertEquals(StartupPhase.Failed(damaged), state.phase.value)
            state.retry(this)
            assertEquals(StartupPhase.Ready, state.phase.first { it !is StartupPhase.Starting })
            assertEquals(2, opens)
        } finally {
            net.bible.service.db.DamagedDataRecovery.route = previousRoute
        }
    }

    @Test
    fun aRunningReportDisablesTheChoices() = runComposeUiTest {
        val release = CompletableDeferred<Unit>()
        setContent {
            CorruptDatabaseRecovery(
                damaged, dark = false, strings = iosStrings("en"),
                onReport = { release.await() }, onRetry = {}, reset = { error("must not reset") },
            ) { _ -> error("backup not opened") }
        }
        onNodeWithTag("corrupt-database-report").performClick()
        onNodeWithTag("corrupt-database-reset").assertIsNotEnabled()
        onNodeWithTag("corrupt-database-backup").assertIsNotEnabled()
        release.complete(Unit)
    }

    @Test
    fun backupReplacesTheScreenAndItsBackRetriesTheStartup() = runComposeUiTest {
        var retries = 0
        setContent {
            CorruptDatabaseRecovery(
                damaged, dark = false, strings = iosStrings("en"),
                onReport = {}, onRetry = { retries++ }, reset = { error("must not reset") },
            ) { back ->
                androidx.compose.material3.Button(onClick = back) { Text("backup-screen") }
            }
        }
        onNodeWithTag("corrupt-database-backup").performClick()
        onNodeWithTag("corrupt-database").assertDoesNotExist()
        onNodeWithText("backup-screen").performClick()
        runOnIdle { assertEquals(1, retries) }
        onNodeWithTag("corrupt-database").assertExists()
    }

    @Test
    fun hostStartupStateRetriesOnlyAfterAFailure() = runTest(timeout = 30.seconds) {
        val failNext = MutableStateFlow(true)
        var opens = 0
        val state = HostAppStartupState(open = {
            opens++
            if (failNext.value) throw damaged
        })
        state.retry(this) // not failed yet: nothing happens
        assertEquals(0, opens)
        state.launch(this)
        val failed = state.phase.first { it !is StartupPhase.Starting }
        assertIs<StartupPhase.Failed>(failed)
        failNext.value = false
        state.retry(this)
        assertEquals(StartupPhase.Ready, state.phase.first { it !is StartupPhase.Starting })
        assertEquals(2, opens)
        state.retry(this) // ready: nothing happens
        assertEquals(2, opens)
    }
}
