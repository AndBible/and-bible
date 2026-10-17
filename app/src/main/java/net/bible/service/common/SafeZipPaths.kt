package net.bible.service.common

/**
 * Normalizes a ZIP entry name for extraction under a target directory (zip-slip guard).
 *
 * Backslashes become `/`; empty and `.` segments are dropped. Returns `null` when the name is
 * absolute (leading `/` or a drive letter such as `C:`) or contains a `..` segment, i.e. when it
 * could resolve outside the target directory.
 */
fun safeZipEntryPath(entryName: String): String? {
    val normalized = entryName.replace('\\', '/')
    if (normalized.startsWith("/")) return null
    if (normalized.length >= 2 && normalized[1] == ':' && normalized[0].isLetter()) return null
    val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
    if (segments.any { it == ".." }) return null
    return segments.joinToString("/")
}
