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

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val inTransactionOnThread = ThreadLocal<Boolean?>()

private object DbTransactionMarkerElement : AbstractCoroutineContextElement(Key), ThreadContextElement<Boolean?> {
    object Key : CoroutineContext.Key<DbTransactionMarkerElement>
    override fun updateThreadContext(context: CoroutineContext): Boolean? =
        inTransactionOnThread.get().also { inTransactionOnThread.set(true) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: Boolean?) = inTransactionOnThread.set(oldState)
}

actual val DbTransactionMarker: CoroutineContext = DbTransactionMarkerElement

actual fun isInDbTransactionOnThread(): Boolean = inTransactionOnThread.get() == true
