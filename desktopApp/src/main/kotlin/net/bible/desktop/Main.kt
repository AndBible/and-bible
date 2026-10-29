package net.bible.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.bible.sharedui.webview.PlaceholderBibleWebView

fun main() = application {
    Window(title = "AndBible", onCloseRequest = ::exitApplication) {
        DesktopPocRoot("{}", { c, m -> PlaceholderBibleWebView(c, m) })
    }
}
