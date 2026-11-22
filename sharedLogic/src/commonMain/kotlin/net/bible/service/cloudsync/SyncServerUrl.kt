package net.bible.service.cloudsync

/**
 * Keep sync roots slash-free so backend paths can be appended without a double slash, and default a
 * scheme-less address to `https://`. An explicit scheme (also `http://`, refused later) is kept as typed.
 */
fun normalizeSyncServerUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    return if (trimmed.isEmpty() || "://" in trimmed) trimmed else "https://$trimmed"
}
