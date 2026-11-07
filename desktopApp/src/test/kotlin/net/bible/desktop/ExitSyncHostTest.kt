package net.bible.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class ExitSyncHostTest {
    @Test fun nullTasksRendersNothing() = runComposeUiTest {
        setContent { ExitSyncHost(null) }
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
    }

    @Test fun nothingShownWhenSyncingNeverTurnsOn() = runComposeUiTest {
        setContent { ExitSyncHost(DesktopExitTasks()) }
        mainClock.advanceTimeBy(600)
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
    }

    @Test fun nothingShownWhenSyncingLastsUnder400ms() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        tasks.register("sync") { beginVisibleWork(); started.complete(Unit); finish.await() }
        val job = CoroutineScope(Dispatchers.IO).async { tasks.runAll(10_000) }
        runBlocking { started.await() }
        mainClock.autoAdvance = false
        setContent { ExitSyncHost(tasks) }
        mainClock.advanceTimeBy(200)
        finish.complete(Unit)
        runBlocking { job.await() }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
    }

    @Test fun interruptedSyncRestartsDelay() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        val firstStarted = CompletableDeferred<Unit>()
        val firstFinish = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val secondFinish = CompletableDeferred<Unit>()
        tasks.register("first") { beginVisibleWork(); firstStarted.complete(Unit); firstFinish.await() }
        tasks.register("second") { secondStarted.await(); beginVisibleWork(); secondFinish.await() }
        val job = CoroutineScope(Dispatchers.IO).async { tasks.runAll(10_000) }
        runBlocking { firstStarted.await() }
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncHost(tasks) }
        } }
        mainClock.advanceTimeBy(250)
        firstFinish.complete(Unit)
        runBlocking { kotlinx.coroutines.withTimeout(5_000) { tasks.syncing.first { !it } } }
        mainClock.advanceTimeBy(32)
        secondStarted.complete(Unit)
        runBlocking { kotlinx.coroutines.withTimeout(5_000) { tasks.syncing.first { it } } }
        mainClock.advanceTimeBy(250)
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
        mainClock.advanceTimeBy(300)
        onNodeWithText("Synchronizing changes…").assertIsDisplayed()
        secondFinish.complete(Unit)
        runBlocking { job.await() }
    }

    @Test fun externalSkipRetainsClosingAfterSyncEnds() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        val started = CompletableDeferred<Unit>()
        tasks.register("sync") { beginVisibleWork(); started.complete(Unit); CompletableDeferred<Unit>().await() }
        val job = CoroutineScope(Dispatchers.IO).async { tasks.runAll(10_000) }
        runBlocking { started.await() }
        var skip by androidx.compose.runtime.mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncHost(tasks, skipRequested = skip) }
        } }
        mainClock.advanceTimeBy(600)
        onNodeWithText("Synchronizing changes…").assertIsDisplayed()
        runOnUiThread { skip = true; tasks.skipRemaining() }
        runBlocking { job.await() }
        mainClock.advanceTimeBy(100)
        onNodeWithText("Closing…").assertIsDisplayed()
    }

    @Test fun shownAfter400msOfSyncingAndQuitRetainsClosing() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        val started = CompletableDeferred<Unit>()
        tasks.register("sync") { beginVisibleWork(); started.complete(Unit); CompletableDeferred<Unit>().await() }
        val job = CoroutineScope(Dispatchers.IO).async { tasks.runAll(10_000) }
        runBlocking { started.await() }
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncHost(tasks) }
        } }
        mainClock.advanceTimeBy(200)
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
        mainClock.advanceTimeBy(500)
        onNodeWithText("Synchronizing changes…").assertIsDisplayed()
        onNodeWithText("Quit now").performClick()
        assertFalse(runBlocking { job.await() })
        mainClock.advanceTimeBy(100)
        onNodeWithText("Closing…").assertIsDisplayed()
    }
}
