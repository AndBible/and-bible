package net.bible.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.bible.sharedui.poc.IosPocApp
import net.bible.sharedui.poc.PocScenario
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.webview.BibleWebViewFactory
import net.bible.sharedui.webview.LocalBibleWebView

/** Desktop root: the shared BibleView PoC with English strings and the given pane factory. */
@Composable
fun DesktopPocRoot(
    documentJson: String,
    webView: BibleWebViewFactory,
    scenario: PocScenario = PocScenario.SPLIT2,
    darkTheme: Boolean = false,
) {
    CompositionLocalProvider(
        LocalStrings provides iosStrings("en"),
        LocalBibleWebView provides webView,
    ) {
        IosPocApp(scenario, documentJson, darkTheme)
    }
}
