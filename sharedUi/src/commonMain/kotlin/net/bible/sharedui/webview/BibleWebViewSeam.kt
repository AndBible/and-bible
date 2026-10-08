package net.bible.sharedui.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.bible.sharedui.poc.LocalPocPaneCreated
import net.bible.sharedui.poc.PocBibleViewController

/** Platform seam: draws one BibleView pane bound to [controller]. iOS supplies a WKWebView, tests a placeholder. */
typealias BibleWebViewFactory = @Composable (controller: PocBibleViewController, modifier: Modifier) -> Unit

val LocalBibleWebView: ProvidableCompositionLocal<BibleWebViewFactory> =
    staticCompositionLocalOf { { controller, modifier -> PlaceholderBibleWebView(controller, modifier) } }

/** Stand-in pane for desktop tests and previews: a tagged box showing the controller's title. */
@Composable
fun PlaceholderBibleWebView(controller: PocBibleViewController, modifier: Modifier) {
    val verse by controller.currentVerse.collectAsState()
    val onCreated = LocalPocPaneCreated.current
    DisposableEffect(controller.windowId) {
        onCreated(controller.windowId)
        onDispose {}
    }
    Box(modifier.testTag("pane-${controller.windowId}")) { Text(controller.titleFor(verse)) }
}
