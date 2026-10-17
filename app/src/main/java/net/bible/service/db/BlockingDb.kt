/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

class BlockingDbInTransaction : IllegalStateException(
    "blockingAtEdge called inside a database transaction: a nested runBlocking cannot use the transaction's " +
        "connection and would wait for it forever. Make the caller suspend instead.")

/**
 * The only bridge allowed after L1a (spec §2 M1 exception): a synchronous Android/JSword edge that cannot
 * suspend. Every call carries a same-line `// L1-edge: <reason>`; `scripts/l1-readiness.sh --check` enforces it.
 * Throws [BlockingDbInTransaction] inside a marked transaction (the guard of the retired `blockingDb`).
 */
fun <T> blockingAtEdge(block: suspend CoroutineScope.() -> T): T {
    if (isInDbTransactionOnThread()) throw BlockingDbInTransaction()
    return runBlocking(block = block)
}
