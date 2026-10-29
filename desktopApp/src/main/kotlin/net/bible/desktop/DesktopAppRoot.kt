/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopGateOutcome
import net.bible.sharedui.desktop.assembly.DesktopStartupGate
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.host.app.AppHostPlatform
import net.bible.sharedui.host.app.IosApp
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.webview.BibleWebViewFactory
import net.bible.sharedui.webview.LocalBibleWebView
import net.bible.sharedui.webview.PlaceholderBibleWebView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the desktop window shows, and the startup behind it. [start] is the blocking startup
 * (`DesktopAppStartup.ensureStarted`; `openDatabases` false is the crash-recovery backup path); it runs on
 * [Dispatchers.IO], never on the UI thread. [platform] is the `AppHostPlatform` the app root gets (built on first use).
 * [crashRecoveryRequired] puts the crash-recovery gate before the startup (`{ false }` until D3d's crash tracker),
 * [reportBug] is that screen's report action and [onExit] its "close the app" action.
 */
class DesktopStartupState(
    val language: String,
    private val start: (openDatabases: Boolean) -> StartupPhase,
    val platform: () -> AppHostPlatform,
    val bibleWebView: BibleWebViewFactory = { controller, modifier -> PlaceholderBibleWebView(controller, modifier) },
    val crashRecoveryRequired: () -> Boolean = { false },
    val reportBug: suspend () -> Unit = {},
    val onExit: () -> Unit = {},
    val onStartupReady: () -> Unit = {},
    val onCleanExit: () -> Unit = {},
) {
    private val launched = AtomicBoolean(false)
    private val _phase = MutableStateFlow<StartupPhase>(StartupPhase.Starting)

    /** The normal startup's phase; it leaves [StartupPhase.Starting] once. */
    val phase: StateFlow<StartupPhase> = _phase

    /**
     * Starts the normal startup on [scope] (Dispatchers.IO) unless it already started: `main()` calls it before the
     * window shows when no gate is up, the root once the gate chain resolved. A throw of [start] becomes the failure.
     */
    fun launch(scope: CoroutineScope) {
        if (!launched.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val result = runStart(openDatabases = true)
            if (result is StartupPhase.Ready) onStartupReady()
            _phase.value = result
        }
    }

    /**
     * Waits until a launched normal startup has left [StartupPhase.Starting], at most [timeoutMs]. True when none was
     * launched or it settled in time.
     */
    suspend fun awaitStartupSettled(timeoutMs: Long): Boolean {
        if (!launched.get()) return true
        return withTimeoutOrNull(timeoutMs) { phase.first { it !is StartupPhase.Starting } } != null
    }

    /** The recovery path's start: Koin only, the databases stay closed. */
    internal suspend fun startForRecovery(): StartupPhase = withContext(Dispatchers.IO) { runStart(openDatabases = false) }

    private fun runStart(openDatabases: Boolean): StartupPhase =
        try {
            start(openDatabases)
        } catch (e: Throwable) {
            StartupPhase.Failed(e)
        }
}

/**
 * The desktop window content (the counterpart of iOS `MainViewController`'s gate chain): the crash-recovery gate when
 * required, then the splash while the startup runs, the startup-failed screen with the failure's message (it names the
 * data directory), and once Ready the real app root `IosApp()` with [LocalBibleWebView] and the UI language's
 * [LocalLayoutDirection] provided, as on iOS. Splash, failure and gate follow the system appearance (no settings read
 * before Ready).
 */
@Composable
fun DesktopAppRoot(state: DesktopStartupState) {
    val strings = remember(state.language) { iosStrings(state.language) }
    val dark = isSystemInDarkTheme()
    val layoutDirection = if (DesktopUiLanguage.isRtl(state.language)) LayoutDirection.Rtl else LayoutDirection.Ltr
    DesktopStartupGate(state.crashRecoveryRequired, dark, strings, state.reportBug) { outcome, returnFromBackup ->
        when (outcome) {
            DesktopGateOutcome.Exit -> LaunchedEffect(Unit) { state.onExit() }
            DesktopGateOutcome.OpenBackup -> {
                val phase by produceState<StartupPhase>(StartupPhase.Starting) { value = state.startForRecovery() }
                when (val p = phase) {
                    StartupPhase.Starting -> StartupSplash(dark)
                    is StartupPhase.Failed -> StartupFailed(dark, strings.errorOccurred, p.error)
                    StartupPhase.Ready -> AppProviders(state, layoutDirection) {
                        IosApp(state.platform(), NavRoutes.BACKUP, dark, returnFromBackup)
                    }
                }
            }
            DesktopGateOutcome.Continue -> {
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { state.launch(scope) }
                val phase by state.phase.collectAsState()
                when (val p = phase) {
                    StartupPhase.Starting -> StartupSplash(dark)
                    is StartupPhase.Failed -> StartupFailed(dark, strings.errorOccurred, p.error)
                    StartupPhase.Ready -> AppProviders(state, layoutDirection) { IosApp(state.platform()) }
                }
            }
        }
    }
}

@Composable
private fun AppProviders(state: DesktopStartupState, layoutDirection: LayoutDirection, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBibleWebView provides state.bibleWebView, LocalLayoutDirection provides layoutDirection, content = content)
}

@Composable
private fun StartupSplash(dark: Boolean) {
    AbTheme(darkTheme = dark) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-splash"),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/** The failure screen: the generic error text and the failure's own message (which names the data directory). */
@Composable
private fun StartupFailed(dark: Boolean, title: String, error: Throwable) {
    AbTheme(darkTheme = dark) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).testTag("desktop-startup-failed"),
            contentAlignment = Alignment.Center,
        ) {
            val detail = error.message ?: error.toString()
            Text("$title\n\n$detail", color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        }
    }
}
