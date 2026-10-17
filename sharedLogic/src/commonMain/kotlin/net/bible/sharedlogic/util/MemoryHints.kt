package net.bible.sharedlogic.util

/** Asks the runtime to collect garbage (`System.gc()` on the JVM and Android; no-op on iOS). */
expect fun requestGarbageCollection()
