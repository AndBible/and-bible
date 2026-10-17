package net.bible.android.platform

import net.bible.service.db.blockingAtEdge
import net.bible.sharedlogic.platform.FirstLoadEdge

/** Android [FirstLoadEdge]: blocks the calling thread through [blockingAtEdge]. */
object AndroidFirstLoadEdge : FirstLoadEdge {
    override fun <T> awaitFirstLoad(body: suspend () -> T): T =
        blockingAtEdge { body() } // L1-edge: activeWindow read or host bootstrap before the first load finished
}
