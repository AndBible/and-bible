package net.bible.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.IosDeviceSettings
import androidx.navigation.compose.composable
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ios.app.IosApp
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAppStartupState
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.assembly.resolveDeviceUiLanguage
import net.bible.sharedui.assembly.logAssemblyProbe
import net.bible.sharedui.theme.AbTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.platform.IosPlatformBootstrap
import net.bible.sharedui.poc.IosPocApp
import net.bible.sharedui.poc.PocScenario
import net.bible.sharedui.probe.PlatformProbeScreen
import kotlin.native.Platform
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.webview.IosBibleWebViewFactory
import net.bible.sharedui.webview.LocalBibleWebView
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.UIKit.UIViewController

/** App-level scope of the cold start; a handler logs, because an uncaught exception in a coroutine aborts the process. */
private val startupScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> nsLog("UNCAUGHT in startup scope: ${e.stackTraceToString()}") },
)

/** One per process: the Swift host may build the view controller more than once. */
private val startupState = IosAppStartupState()

/**
 * iOS entry point, wrapped by the Swift host (`iosApp/iosApp/iOSApp.swift`). I0: shows the PoC app;
 * `SCREENSHOT_SCENARIO` and `POC_DARK` (environment, set by XCUITest) pick the scenario and theme.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // Kotlin/Native prints an uncaught exception to stderr, which neither the unified log nor the .ips crash report
    // keeps. Log it through NSLog first; the process still terminates afterwards.
    setUnhandledExceptionHook { nsLog("UNCAUGHT Kotlin exception: ${it.stackTraceToString()}") }
    // C4: the UI language is resolved once, from locale_pref, the device languages and the languages the strings cover.
    val language = resolveDeviceUiLanguage(IosDeviceSettings())
    // Stage 1 (main thread, quick, no database); stage 2 (databases, workspace) runs off the main thread behind the splash.
    val startError = try {
        IosAppStartup.start(IosAppConfig(uiLanguage = language))
        null
    } catch (e: Throwable) {
        e
    }
    if (startError != null) startupState.fail(startError) else startupState.launch(startupScope)
    return ComposeUIViewController {
        val env = remember { NSProcessInfo.processInfo.environment }
        val scenario = remember { PocScenario.fromName(env["SCREENSHOT_SCENARIO"] as? String) }
        val dark = remember { (env["POC_DARK"] as? String) == "1" }
        if (scenario.shouldShowPlatformProbe(Platform.isDebugBinary, env["PLATFORM_PROBE"] as? String)) {
            PlatformProbeScreen(darkTheme = dark)
            return@ComposeUIViewController
        }
        val phase by startupState.phase.collectAsState()
        val strings = remember { iosStrings(language) }
        when (val p = phase) {
            StartupPhase.Starting -> { StartupSplash(dark); return@ComposeUIViewController }
            is StartupPhase.Failed -> {
                LaunchedEffect(p) { nsLog("startup failed: ${p.error.stackTraceToString()}") }
                StartupFailed(dark, strings.errorOccurred)
                return@ComposeUIViewController
            }
            StartupPhase.Ready -> Unit
        }
        LaunchedEffect(Unit) {
            // Off main: schedule() reads AppSettings (database-backed) and resolves Koin definitions.
            withContext(Dispatchers.Default) {
                runCatching { IosPlatformBootstrap.scheduleBackgroundSync() }
                    .onFailure { nsLog("background sync scheduling failed: ${it.stackTraceToString()}") }
            }
        }
        if (Platform.isDebugBinary) {
            LaunchedEffect(Unit) {
                withContext(Dispatchers.Default) {
                    runCatching { logAssemblyProbe() }.onFailure { nsLog("I2 assembly: probe crashed: ${it.stackTraceToString()}") }
                }
            }
        }
        // Production path: the real app root. The PoC below stays only for SCREENSHOT_SCENARIO runs (spec C4).
        if (env["SCREENSHOT_SCENARIO"] as? String == null) {
            CompositionLocalProvider(LocalBibleWebView provides IosBibleWebViewFactory) {
                IosApp(strings) { _ ->
                    // Reading destination placeholder until the reading host lands.
                    composable(NavRoutes.READING) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.appName) }
                    }
                }
            }
            return@ComposeUIViewController
        }
        val documentJson = remember {
            NSBundle.mainBundle.pathForResource("poc-document", "json", "bibleview-js")
                ?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) } ?: "{}"
        }
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalBibleWebView provides IosBibleWebViewFactory,
        ) {
            IosPocApp(scenario, documentJson, darkTheme = dark)
        }
    }
}

@Composable
private fun StartupSplash(dark: Boolean) {
    AbTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun StartupFailed(dark: Boolean, message: String) {
    AbTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text(message, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
