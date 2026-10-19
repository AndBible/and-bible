package net.bible.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import net.bible.sharedui.ios.discrete.IosAppIcon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.bible.sharedui.ios.startup.*
import net.bible.sharedui.ios.discrete.IosCalculatorGate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.ios.app.IosApp
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAppStartupState
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.assembly.resolveDeviceUiLanguage
import net.bible.sharedui.assembly.logAssemblyProbe
import net.bible.sharedui.theme.AbTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedui.ios.report.IosBugReport
import net.bible.sharedui.assembly.iosStringTable
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
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController

/** App-level scope of the cold start; a handler logs, because an uncaught exception in a coroutine aborts the process. */
private val startupScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> nsLog("UNCAUGHT in startup scope: ${e.stackTraceToString()}") },
)

/** One per process: the Swift host may build the view controller more than once. */
private val startupState = IosAppStartupState()
private val crashTracker = IosCrashTracker(IosDeviceSettings())

/**
 * iOS entry point, wrapped by the Swift host (`iosApp/iosApp/iOSApp.swift`). Shows the real app ([IosApp]) once
 * startup is Ready; with `SCREENSHOT_SCENARIO` set (XCUITest) it shows the I0 PoC app instead, and `POC_DARK` picks
 * that scenario's theme. The discrete check implements U7: hide content before PIN unlock;
 * ADR 0007 does not fake the app name.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // Kotlin/Native prints an uncaught exception to stderr, which neither the unified log nor the .ips crash report
    // keeps. Log it through NSLog first; the process still terminates afterwards.
    setUnhandledExceptionHook {
        crashTracker.recordUncaught(it)
        nsLog("UNCAUGHT Kotlin exception: ${it.stackTraceToString()}")
        platform.posix.abort()
    }
    IosDebugLaunchArgs.seedCrashRecovery(IosDeviceSettings())
    crashTracker.onLaunch()
    IosPlatformBootstrap.registerCrashLifecycle(crashTracker)
    // C4: the UI language is resolved once, from locale_pref, the device languages and the languages the strings cover.
    val settings = IosDeviceSettings()
    val language = resolveDeviceUiLanguage(settings)
    val requiresCalculator = settings.getBoolean("discrete_mode", false) || settings.getBoolean("show_calculator", false)
    return ComposeUIViewController {
        val env = remember { NSProcessInfo.processInfo.environment }
        val scenario = remember { PocScenario.fromName(env["SCREENSHOT_SCENARIO"] as? String) }
        val dark = remember { (env["POC_DARK"] as? String) == "1" }
        // Splash and failure screens of the production path follow the system appearance (no AppSettings read before
        // Ready); the PoC scenarios keep POC_DARK.
        val splashDark = remember {
            if (env["SCREENSHOT_SCENARIO"] as? String != null) dark
            else UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        val strings = remember { iosStrings(language) }
        val reportScope = rememberCoroutineScope()
        val reporter = remember { IosBugReport(iosStringTable(language)) }
        var reporting by remember { mutableStateOf(false) }
        // Do not start either startup stage (or show a splash) behind the disguise.
        // Not saveable: a recreated host must pass the gate again.
        val gates = remember {
            IosStartupGates(listOf(
                StartupGate.Calculator { requiresCalculator },
                StartupGate.CrashRecovery { crashTracker.shouldOfferRecovery() },
            )).apply { advance() }
        }
        var gateRevision by remember { mutableStateOf(0) }
        gateRevision // changes when a gate resolves
        val resolve: (StartupGateResult) -> Unit = { result ->
            if (gates.current is StartupGate.CrashRecovery && result == StartupGateResult.Continue) crashTracker.continueStartup()
            gates.advance(result)
            gateRevision++
        }
        when (gates.current) {
            is StartupGate.Calculator -> {
                CompositionLocalProvider(LocalStrings provides strings) {
                    AbTheme(darkTheme = splashDark) { IosCalculatorGate(settings) { resolve(StartupGateResult.Continue) } }
                }
                return@ComposeUIViewController
            }
            is StartupGate.CrashRecovery -> {
                CompositionLocalProvider(LocalStrings provides strings) {
                    AbTheme(darkTheme = splashDark) {
                        CrashRecoveryScreen(
                            onReport = {
                                if (!reporting) {
                                    reporting = true
                                    reportScope.launch {
                                        try { reporter.report() }
                                        finally { reporting = false }
                                    }
                                }
                            },
                            onResolve = resolve,
                        )
                    }
                }
                return@ComposeUIViewController
            }
            null -> Unit
        }
        if (gates.result == StartupGateResult.Exit) return@ComposeUIViewController
        val recoveryBackup = gates.result == StartupGateResult.OpenBackup
        var recoveryBindingsReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (IosDebugLaunchArgs.resetData()) IosDebugLaunchArgs.resetAppData()
            val startError = try {
                IosAppStartup.start(IosAppConfig(uiLanguage = language))
                null
            } catch (e: Throwable) {
                e
            }
            if (startError != null) startupState.fail(startError)
            else if (recoveryBackup) recoveryBindingsReady = true
            else startupState.launch(startupScope)
        }
        if (recoveryBackup) {
            val recoveryPhase by startupState.phase.collectAsState()
            if (recoveryPhase is StartupPhase.Failed) StartupFailed(splashDark, strings.errorOccurred)
            else if (!recoveryBindingsReady) StartupSplash(splashDark)
            else CompositionLocalProvider(LocalBibleWebView provides IosBibleWebViewFactory) {
                IosApp(strings, net.bible.sharedcore.nav.NavRoutes.BACKUP, splashDark) {
                    gates.returnFromBackup()
                    gateRevision++
                }
            }
            return@ComposeUIViewController
        }
        if (scenario.shouldShowPlatformProbe(Platform.isDebugBinary, env["PLATFORM_PROBE"] as? String)) {
            PlatformProbeScreen(darkTheme = dark)
            return@ComposeUIViewController
        }
        val phase by startupState.phase.collectAsState()
        when (val p = phase) {
            StartupPhase.Starting -> { StartupSplash(splashDark); return@ComposeUIViewController }
            is StartupPhase.Failed -> {
                LaunchedEffect(p) { nsLog("startup failed: ${p.error.stackTraceToString()}") }
                StartupFailed(splashDark, strings.errorOccurred)
                return@ComposeUIViewController
            }
            StartupPhase.Ready -> Unit
        }
        DisposableEffect(Unit) {
            val cancel = settings.observeBoolean("discrete_mode", false, IosAppIcon::apply)
            onDispose { cancel() }
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
                IosApp(strings)
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
