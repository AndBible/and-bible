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
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.db

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Deliberately an empty context. Kotlin/Native has no `ThreadContextElement` (kotlinx.coroutines 1.11.0), and the
 * substitutes are unsound: a plain thread-local or global flag is lost on a dispatcher switch or leaks to the thread
 * the body left. The guard is also unnecessary on iOS: `blockingAtEdge` is an Android/JSword-only edge, so iOS code
 * must never call a blocking database bridge.
 */
actual val DbTransactionMarker: CoroutineContext = EmptyCoroutineContext

actual fun isInDbTransactionOnThread(): Boolean = false
