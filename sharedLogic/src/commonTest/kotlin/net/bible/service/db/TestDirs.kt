package net.bible.service.db

/** A fresh writable directory; JVM tests skip fsync unless [durable] preserves recovery/journal behavior. */
expect fun newTestDir(durable: Boolean = false): String
