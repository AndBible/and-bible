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

/**
 * Marks the coroutine as running a database transaction body, so the app's `blockingAtEdge` can refuse to run
 * there. On JVM/Android it is a thread-context element re-applied on every resumption (the mark follows the body
 * across `withContext` thread hops); on iOS there is no blocking edge, so it is an empty context.
 */
expect val DbTransactionMarker: CoroutineContext

/** True while the current thread runs a body marked with [DbTransactionMarker]. Always false on iOS. */
expect fun isInDbTransactionOnThread(): Boolean
