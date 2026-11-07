package net.bible.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import net.bible.sharedui.host.app.LocalHostAppearanceCapture
import net.bible.sharedui.theme.LocalDisableAnimations
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedui.theme.abColorScheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    // The wrapper must not depend on a successful settings/appearance sample.
    @Test fun closeBeforeAppearanceReadyStillShowsStatusAndQuit() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        org.koin.core.context.startKoin { modules(org.koin.dsl.module { single { tasks } }) }
        val started = CompletableDeferred<Unit>()
        tasks.register("sync") { beginVisibleWork(); started.complete(Unit); CompletableDeferred<Unit>().await() }
        val job = CoroutineScope(Dispatchers.IO).async { tasks.runAll(10_000) }
        try {
            runBlocking { started.await() }
            mainClock.autoAdvance = false
            setContent { ExitSyncWindow(true, "en", false, { tasks.skipRemaining() }) { error("closed content entered") } }
            mainClock.advanceTimeBy(450)
            onNodeWithText("Synchronizing changes…").assertIsDisplayed()
            onNodeWithTag("exitSyncStaticIcon").assertExists()
            val background = onRoot().captureToImage().toPixelMap()[0, 0]
            assertEquals(background.red, background.green)
            assertEquals(background.green, background.blue)
            onNodeWithText("Quit now").performClick()
            assertFalse(runBlocking { job.await() })
        } finally {
            tasks.skipRemaining()
            runBlocking { job.await() }
            org.koin.core.context.stopKoin()
        }
    }

    // Dropping the resolved context, polling it, or re-resolving a default seed breaks this test.
    @Test fun immediatelyClosingRetainsLiveResolvedAppearanceAndLanguage() = runComposeUiTest {
        var changed by mutableStateOf(false)
        var closing by mutableStateOf(false)
        val appearance = ExitSyncAppearance()
        val seed = 0xFFB34819.toInt()
        mainClock.autoAdvance = false
        setContent {
            ExitSyncWindow(closing, "en", true, {}, appearance = appearance, tasks = androidx.compose.runtime.remember { DesktopExitTasks() }) {
                CompositionLocalProvider(LocalStrings provides iosStrings(if (changed) "fi" else "en")) {
                    AbTheme(seedArgb = if (changed) seed else null, darkTheme = changed,
                        colorMode = if (changed) DisplayColorMode.MONOCHROME else DisplayColorMode.NORMAL,
                        disableAnimations = changed, paletteStyle = ThemePaletteStyle.DEFAULT) {
                        LocalHostAppearanceCapture.current()
                    }
                }
            }
            if (closing) appearance.Content {
                assertTrue(LocalIsDarkTheme.current)
                assertTrue(LocalDisableAnimations.current)
                assertEquals(DisplayColorMode.MONOCHROME, LocalDisplayColorMode.current)
                assertEquals(abColorScheme(seed, true, DisplayColorMode.MONOCHROME, ThemePaletteStyle.DEFAULT).primary, MaterialTheme.colorScheme.primary)
                Text(LocalStrings.current.exitSyncTitle)
            }
        }
        mainClock.advanceTimeByFrame()
        runOnUiThread { changed = true }
        mainClock.advanceTimeByFrame() // Apply the new HostApp theme, without a 500 ms poll.
        runOnUiThread { closing = true }
        mainClock.advanceTimeByFrame()
        onNodeWithText(iosStrings("fi").exitSyncTitle).assertIsDisplayed()
        onNodeWithText(iosStrings("fi").exitSyncClosing).assertIsDisplayed()
        onNodeWithTag("exitSyncStaticIcon").assertExists()
        assertEquals(androidx.compose.ui.graphics.Color.Black, onRoot().captureToImage().toPixelMap()[0, 0])
    }

    @Test fun workspacePaletteIsRetainedRatherThanDefaultedOnClose() = runComposeUiTest {
        var seed by mutableStateOf<Int?>(null)
        var closing by mutableStateOf(false)
        val appearance = ExitSyncAppearance()
        mainClock.autoAdvance = false
        setContent {
            ExitSyncWindow(closing, "en", true, {}, appearance = appearance, tasks = androidx.compose.runtime.remember { DesktopExitTasks() }) {
                AbTheme(seedArgb = seed, darkTheme = false, disableAnimations = true) {
                    LocalHostAppearanceCapture.current()
                }
            }
            if (closing) appearance.Content {
                assertEquals(abColorScheme(0xFFB34819.toInt(), false, DisplayColorMode.NORMAL, ThemePaletteStyle.DEFAULT).primary, MaterialTheme.colorScheme.primary)
                Text("retained workspace palette")
            }
        }
        mainClock.advanceTimeByFrame()
        runOnUiThread { seed = 0xFFB34819.toInt() }
        mainClock.advanceTimeByFrame()
        runOnUiThread { closing = true }
        mainClock.advanceTimeByFrame()
        onNodeWithText("retained workspace palette").assertIsDisplayed()
    }

    @Test fun repeatedWindowCloseSkipsWithoutSecondPersistenceOrReenteringPanes() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        val closes = DesktopCloseRequests(tasks = { tasks })
        val starts = java.util.concurrent.atomic.AtomicInteger()
        var panesDisposed = false
        var paneEntries = 0
        val started = CompletableDeferred<Unit>()
        val persisted = CompletableDeferred<Unit>()
        tasks.register("sync") { beginVisibleWork(); started.complete(Unit); CompletableDeferred<Unit>().await() }
        val persistence = net.bible.sharedui.desktop.assembly.DesktopExitPersistence(registerHook = {}, persist = {
            assertTrue(panesDisposed, "Persistence began before the normal panes were removed")
            starts.incrementAndGet()
            runBlocking { tasks.runAll(10_000) }
            persisted.complete(Unit)
        })
        mainClock.autoAdvance = false
        setContent {
            if (closes.closing) LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(Dispatchers.IO) { persistence.persistOnce() }
            }
            ExitSyncWindow(closes.closing, "en", closes.skipRequested, closes::requestClose, tasks = tasks) {
                DisposableEffect(Unit) { paneEntries++; onDispose { panesDisposed = true } }
            }
        }
        mainClock.advanceTimeByFrame()
        runOnUiThread { closes.requestClose() }
        mainClock.advanceTimeByFrame()
        runBlocking { kotlinx.coroutines.withTimeout(5_000) { started.await() } }
        mainClock.advanceTimeBy(450)
        onNodeWithText("Synchronizing changes…").assertIsDisplayed()
        runOnUiThread { closes.requestClose(); closes.requestClose() }
        runBlocking { kotlinx.coroutines.withTimeout(5_000) { persisted.await() } }
        mainClock.advanceTimeByFrame()
        onNodeWithText("Closing…").assertIsDisplayed()
        assertEquals(1, starts.get())
        assertEquals(1, paneEntries)
    }

    @Test fun closeBeforeGraphReadyObservesLateSyncTasks() = runComposeUiTest {
        val tasks = DesktopExitTasks()
        val releaseStartup = java.util.concurrent.CountDownLatch(1)
        val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        val state = DesktopStartupState("en", start = {
            check(releaseStartup.await(5, java.util.concurrent.TimeUnit.SECONDS))
            org.koin.core.context.startKoin { modules(org.koin.dsl.module { single { tasks } }) }
            net.bible.sharedui.assembly.StartupPhase.Ready
        }, platform = { error("closing must never recreate HostApp or web panes") })
        state.launch(scope)
        val started = CompletableDeferred<Unit>()
        tasks.register("sync") { beginVisibleWork(); started.complete(Unit); CompletableDeferred<Unit>().await() }
        mainClock.autoAdvance = false
        setContent { ExitSyncWindow(true, "en", false, { tasks.skipRemaining() }, tasks = exitTasksAfterStartup(state)) {} }
        mainClock.advanceTimeByFrame()
        releaseStartup.countDown()
        runBlocking { assertTrue(state.awaitStartupSettled(5_000)) }
        mainClock.advanceTimeByFrame()
        val job = CoroutineScope(Dispatchers.IO).async { tasks.runAll(10_000) }
        try {
            runBlocking { kotlinx.coroutines.withTimeout(5_000) { started.await() } }
            mainClock.advanceTimeBy(450)
            onNodeWithText("Synchronizing changes…").assertIsDisplayed()
            onNodeWithTag("exitSyncStaticIcon").assertExists()
            val background = onRoot().captureToImage().toPixelMap()[0, 0]
            assertEquals(background.red, background.green)
            assertEquals(background.green, background.blue)
            onNodeWithText("Quit now").performClick()
            assertFalse(runBlocking { job.await() })
        } finally {
            tasks.skipRemaining()
            runBlocking { job.await() }
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            org.koin.core.context.stopKoin()
        }
    }

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
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncHost(tasks) }
        } }
        mainClock.advanceTimeBy(200)
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
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
        mainClock.advanceTimeByFrame() // Start the effect at a known frame boundary.
        mainClock.advanceTimeBy(399, ignoreFrameDuration = true)
        onNodeWithText("Synchronizing changes…").assertDoesNotExist()
        mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        mainClock.advanceTimeByFrame() // Apply visibility at the first frame after the 400 ms deadline.
        onNodeWithText("Synchronizing changes…").assertIsDisplayed()
        onNodeWithText("Quit now").performClick()
        assertFalse(runBlocking { job.await() })
        mainClock.advanceTimeBy(100)
        onNodeWithText("Closing…").assertIsDisplayed()
    }
}
