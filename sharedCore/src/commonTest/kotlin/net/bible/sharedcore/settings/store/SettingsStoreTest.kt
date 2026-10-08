package net.bible.sharedcore.settings.store

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class FakeBackend(initial: SettingsSnapshot = SettingsSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())) : SettingsBackend {
    val written = mutableListOf<SettingWrite>()
    val booleans = initial.booleans.toMutableMap(); val longs = initial.longs.toMutableMap()
    val strings = initial.strings.toMutableMap(); val doubles = initial.doubles.toMutableMap()
    var gate: CompletableDeferred<Unit>? = null
    private val lock = Mutex()
    override suspend fun loadAll() = SettingsSnapshot(booleans.toMap(), longs.toMap(), strings.toMap(), doubles.toMap())
    override suspend fun write(write: SettingWrite) {
        gate?.await()
        lock.withLock {
            written += write
            when (write) {
                is SettingWrite.Bool -> if (write.value == null) booleans.remove(write.key) else booleans[write.key] = write.value
                is SettingWrite.Lng -> if (write.value == null) longs.remove(write.key) else longs[write.key] = write.value
                is SettingWrite.Str -> if (write.value == null) strings.remove(write.key) else strings[write.key] = write.value
                is SettingWrite.Dbl -> if (write.value == null) doubles.remove(write.key) else doubles[write.key] = write.value
            }
        }
    }
}

class SettingsStoreTest {
    @Test fun loadedValuesAreReadSynchronously() = runTest {
        val backend = FakeBackend(SettingsSnapshot(mapOf("b" to true), mapOf("l" to 5L), mapOf("s" to "x"), mapOf("d" to 1.5)))
        val store = SettingsStore(backend, backgroundScope).apply { load() }
        assertTrue(store.getBoolean("b", false)); assertEquals(5L, store.getLong("l", 0))
        assertEquals("x", store.getString("s", null)); assertEquals(1.5, store.getDouble("d", 0.0))
        assertEquals("def", store.getString("missing", "def"))
    }

    @Test fun setIsVisibleBeforeTheWriteLands() = runTest {
        val backend = FakeBackend().apply { gate = CompletableDeferred() }
        val store = SettingsStore(backend, backgroundScope).apply { load() }
        store.setString("k", "v")
        assertEquals("v", store.getString("k", null))
        assertTrue(backend.written.isEmpty())
        backend.gate!!.complete(Unit); store.flush()
        assertEquals("v", backend.strings["k"])
    }

    @Test fun nullDeletesInMemoryAndInBackend() = runTest {
        val backend = FakeBackend(SettingsSnapshot(mapOf("b" to true), emptyMap(), emptyMap(), emptyMap()))
        val store = SettingsStore(backend, backgroundScope).apply { load() }
        store.setBoolean("b", null); store.flush()
        assertFalse(store.getBoolean("b", false)); assertFalse("b" in backend.booleans)
    }

    @Test fun writesReachTheBackendInCallOrder() = runTest {
        val backend = FakeBackend()
        val store = SettingsStore(backend, backgroundScope).apply { load() }
        repeat(50) { store.setLong("k", it.toLong()) }
        store.flush()
        assertEquals((0L until 50L).toList(), backend.written.map { (it as SettingWrite.Lng).value })
        assertEquals(49L, backend.longs["k"])
    }

    @Test fun concurrentSetsOfOneKeyEndConsistent() = runBlocking {
        val backend = FakeBackend()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val store = SettingsStore(backend, scope).apply { load() }
        (1..8).map { t -> launch(Dispatchers.Default) { repeat(200) { store.setLong("k", (t * 1000 + it).toLong()) } } }.joinAll()
        store.flush()
        assertEquals(store.getLong("k", -1), backend.longs["k"])
        scope.cancel()
    }

    @Test fun typesAreSeparateNamespaces() = runTest {
        val store = SettingsStore(FakeBackend(), backgroundScope).apply { load() }
        store.setLong("k", 3); store.setString("k", "s")
        assertEquals(3L, store.getLong("k", 0)); assertEquals("s", store.getString("k", null))
    }

    @Test fun changesEmitTheKey() = runTest {
        val store = SettingsStore(FakeBackend(), backgroundScope).apply { load() }
        val got = async { store.changes.first() }
        yield()
        store.setBoolean("x", true)
        assertEquals("x", got.await())
    }
}
