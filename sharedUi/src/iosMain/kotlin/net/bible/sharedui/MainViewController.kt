package net.bible.sharedui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.poc.IosPocApp
import net.bible.sharedui.poc.PocScenario
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

/**
 * iOS entry point, wrapped by the Swift host (`iosApp/iosApp/iOSApp.swift`). I0: shows the PoC app;
 * `SCREENSHOT_SCENARIO` and `POC_DARK` (environment, set by XCUITest) pick the scenario and theme.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // Kotlin/Native prints an uncaught exception to stderr, which neither the unified log nor the .ips crash report
    // keeps. Log it through NSLog first; the process still terminates afterwards.
    setUnhandledExceptionHook { nsLog("UNCAUGHT Kotlin exception: ${it.stackTraceToString()}") }
    return ComposeUIViewController {
        val env = remember { NSProcessInfo.processInfo.environment }
        val scenario = remember { PocScenario.fromName(env["SCREENSHOT_SCENARIO"] as? String) }
        val dark = remember { (env["POC_DARK"] as? String) == "1" }
        val documentJson = remember {
            NSBundle.mainBundle.pathForResource("poc-document", "json", "bibleview-js")
                ?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) } ?: "{}"
        }
        CompositionLocalProvider(
            LocalStrings provides remember { iosStrings("en") },
            LocalBibleWebView provides IosBibleWebViewFactory,
        ) {
            IosPocApp(scenario, documentJson, darkTheme = dark)
        }
    }
}
