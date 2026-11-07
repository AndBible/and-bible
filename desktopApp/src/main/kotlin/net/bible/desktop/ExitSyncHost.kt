package net.bible.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.desktop.exit.ExitSyncStatus
import net.bible.sharedui.host.app.LocalHostAppearanceCapture
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import org.koin.mp.KoinPlatform

/** Retains only resolved locals, never a theme/settings reader or the normal app content. */
internal class ExitSyncAppearance {
    private var context: CompositionLocalContext? = null

    @Composable
    fun Capture() {
        val resolved = currentCompositionLocalContext
        SideEffect { context = resolved }
    }

    @Composable
    fun Content(content: @Composable () -> Unit) {
        val captured = context
        if (captured != null) CompositionLocalProvider(captured, content = content) else content()
    }
}

/** One close transition shared by the WM close action and Quit now in both desktop launchers. */
internal class DesktopCloseRequests(private val tasks: () -> DesktopExitTasks?, private val beforeClose: () -> Unit = {}) {
    var closing by mutableStateOf(false)
        private set
    var skipRequested by mutableStateOf(false)
        private set

    fun requestClose() {
        if (!closing) {
            beforeClose()
            closing = true
        } else if (!skipRequested) {
            skipRequested = true
            tasks()?.skipRemaining()
        }
    }
}

/** Keep observing startup outside the removed app root so an early close can acquire its late graph. */
@Composable
internal fun exitTasksAfterStartup(state: DesktopStartupState): DesktopExitTasks? {
    val phase by state.phase.collectAsState()
    return if (phase is StartupPhase.Starting) null else KoinPlatform.getKoinOrNull()?.getOrNull()
}

@Composable
internal fun ExitSyncWindow(
    closing: Boolean,
    language: String,
    skipRequested: Boolean,
    onSkip: () -> Unit,
    appearance: ExitSyncAppearance = remember { ExitSyncAppearance() },
    tasks: DesktopExitTasks? = KoinPlatform.getKoinOrNull()?.getOrNull(),
    content: @Composable () -> Unit,
) {
    if (!closing) {
        CompositionLocalProvider(LocalHostAppearanceCapture provides { appearance.Capture() }, content = content)
    } else {
        val strings = remember(language) { iosStrings(language) }
        val direction = if (DesktopUiLanguage.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr
        // Before HostApp is ready, use a DB-free, static grayscale fallback. A captured host context overrides it.
        CompositionLocalProvider(LocalStrings provides strings, LocalLayoutDirection provides direction) {
            AbTheme(darkTheme = isSystemInDarkTheme(), colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
                appearance.Content {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ExitSyncHost(tasks, skipRequested = skipRequested, onSkip = onSkip)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExitSyncHost(tasks: DesktopExitTasks?, showAfterMs: Long = 400, skipRequested: Boolean = false, onSkip: () -> Unit = { tasks?.skipRemaining() }) {
    if (tasks == null) return
    val syncing by tasks.syncing.collectAsState()
    var visible by remember(tasks) { mutableStateOf(false) }
    var quitRequested by remember(tasks) { mutableStateOf(false) }
    val quitting = quitRequested || skipRequested
    LaunchedEffect(tasks, syncing) {
        if (syncing) {
            delay(showAfterMs)
            visible = true
        } else if (!quitting) {
            visible = false
        }
    }
    if ((visible && syncing) || quitting) ExitSyncStatus(closing = quitting, onQuitNow = {
        if (!quitting) {
            quitRequested = true
            onSkip()
        }
    })
}
