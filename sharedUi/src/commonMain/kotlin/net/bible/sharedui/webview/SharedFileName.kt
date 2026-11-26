@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

/** Portable file-name stem for exported HTML: preserve the title, but exclude paths and control characters. */
@kotlin.native.HiddenFromObjC
fun sharedFileName(title: String): String {
    val cleaned = title.map { if (it in "/\\:*?\"<>|" || it.isISOControl()) ' ' else it }.joinToString("")
        .trim { it.isWhitespace() || it == '.' }.takeCodePoints(60).trim { it.isWhitespace() || it == '.' }
    val safeName = if (cleaned.isWindowsReservedDeviceName()) {
        val extensionStart = cleaned.indexOf('.')
        if (extensionStart < 0) "${cleaned}_" else cleaned.substring(0, extensionStart) + "_" + cleaned.substring(extensionStart)
    } else {
        cleaned
    }
    return safeName.takeCodePoints(60).trim { it.isWhitespace() || it == '.' }.ifBlank { "shared" }
}

private fun String.isWindowsReservedDeviceName(): Boolean {
    val basename = substringBefore('.').uppercase()
    return basename in setOf("CON", "PRN", "AUX", "NUL") ||
        (basename.length == 4 && basename.startsWith("COM") && basename[3] in '1'..'9') ||
        (basename.length == 4 && basename.startsWith("LPT") && basename[3] in '1'..'9')
}

private fun String.takeCodePoints(limit: Int): String {
    var index = 0
    var count = 0
    while (index < length && count < limit) {
        if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
            index += 2
        } else {
            index++
        }
        count++
    }
    return substring(0, index)
}
