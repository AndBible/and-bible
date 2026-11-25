@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

/** Portable file-name stem for exported HTML: preserve the title, but exclude paths and control characters. */
@kotlin.native.HiddenFromObjC
fun sharedFileName(title: String): String {
    val cleaned = title.map { if (it in "/\\:*?\"<>|" || it.isISOControl()) ' ' else it }.joinToString("")
        .trim { it.isWhitespace() || it == '.' }.take(60).trim { it.isWhitespace() || it == '.' }
    return cleaned.ifBlank { "shared" }
}
