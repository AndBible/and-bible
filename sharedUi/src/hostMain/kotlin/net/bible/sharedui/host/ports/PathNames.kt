package net.bible.sharedui.host.ports

/**
 * The last component of a slash-separated file [path], the same answer as Foundation's
 * `NSString.lastPathComponent`: trailing slashes are ignored, the root "/" stays "/", and an empty path stays empty.
 */
internal fun lastPathComponent(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty()) return if (path.isEmpty()) "" else "/"
    return trimmed.substringAfterLast('/')
}
