package net.bible.android.control.download

/**
 * Asks the platform to keep the process running while a repository transfer is in flight (#3973).
 *
 * iOS suspends a backgrounded app within seconds, which stops an unfinished download with it; the iOS binding wraps
 * the transfer in a UIKit background task, so a short app switch lets the install finish. Platforms that keep a
 * backgrounded process running bind nothing and get [None].
 */
fun interface DownloadKeepAlive {
    /** Starts holding the process for [name]; the returned function releases it and may be called once. */
    fun hold(name: String): () -> Unit

    companion object {
        val None = DownloadKeepAlive { {} }
    }
}
