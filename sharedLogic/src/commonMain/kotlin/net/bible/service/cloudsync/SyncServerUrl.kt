package net.bible.service.cloudsync

/** Keep sync roots slash-free so backend paths can be appended without a double slash. */
fun normalizeSyncServerUrl(url: String): String = url.trim().trimEnd('/')
