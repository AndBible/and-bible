package net.bible.sharedlogic.platform

/**
 * Synchronous edge for the window domain's first load.
 *
 * [awaitFirstLoad] blocks the calling thread until [body] finishes. It is an Android-only edge for the
 * synchronous `activeWindow` read (or host bootstrap) before the first load has completed; iOS decides in I2.
 */
interface FirstLoadEdge {
    fun <T> awaitFirstLoad(body: suspend () -> T): T
}
