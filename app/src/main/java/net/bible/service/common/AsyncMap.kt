package net.bible.service.common

import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

suspend fun <T, V> Collection<T>.asyncMap(action: suspend (T) -> V): Collection<V> = withContext(Dispatchers.IO) {
     map { async { action(it) }}.awaitAll()
}

suspend fun <T, V> Collection<T>.asyncMap(maxThreads: Int, action: suspend (T) -> V): Collection<V> = withContext(Dispatchers.IO) {
    val semaphore = Semaphore(maxThreads)
    map {async {semaphore.withPermit { action(it) } } }.awaitAll()
}
