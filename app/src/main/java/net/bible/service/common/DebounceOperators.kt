/*
 * Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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


import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Debounce operators from https://gist.github.com/Terenfear/a84863be501d3399889455f391eeefe5

/**
 * Constructs a function that processes input data and passes the latest data to [destinationFunction] in the end of every time window with length of [intervalMs].
 */
fun <T> throttleLatest(
    intervalMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var throttleJob: Job? = null
    var latestParam: T
    return { param: T ->
        latestParam = param
        if (throttleJob?.isCompleted != false) {
            throttleJob = coroutineScope.launch {
                delay(intervalMs)
                destinationFunction(latestParam)
            }
        }
    }
}


/**
 * Constructs a function that processes input data and passes it to [destinationFunction] only if there's no new data for at least [waitMs]
 */
fun <T> debounce(
    waitMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
}

fun debounce(
    waitMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: () -> Unit
): () -> Unit {
    var debounceJob: Job? = null
    return {
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(waitMs)
            destinationFunction()
        }
    }
}

/**
 * Runs [block] on the Main thread, blocking the caller until it completes.
 *
 * If already on the Main thread, [block] runs immediately (no dispatch, no risk of
 * deadlocking against Main). If called from a background thread, hops to Main and
 * blocks until [block] finishes.
 *
 * Intended for state that is conceptually "owned" by the Main thread (e.g. in-memory
 * window/history state) but occasionally needs to be read/snapshotted from background
 * work such as cloud sync or database backup, without introducing per-field locking.
 */
inline fun <T> runOnMainBlocking(crossinline block: () -> T): T =
    if (Looper.myLooper() == Looper.getMainLooper()) block()
    else runBlocking(Dispatchers.Main.immediate) { block() }

/**
 * Constructs a function that processes input data and passes the first data to [destinationFunction] and skips all new data for the next [skipMs].
 */
fun <T> throttleFirst(
    skipMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var throttleJob: Job? = null
    return { param: T ->
        if (throttleJob?.isCompleted != false) {
            throttleJob = coroutineScope.launch {
                destinationFunction(param)
                delay(skipMs)
            }
        }
    }
}
