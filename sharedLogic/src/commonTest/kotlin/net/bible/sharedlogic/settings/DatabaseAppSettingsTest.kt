/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedlogic.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import net.bible.service.db.DataBaseNotReady
import net.bible.sharedcore.settings.store.SettingWrite
import net.bible.sharedcore.settings.store.SettingsBackend
import net.bible.sharedcore.settings.store.SettingsSnapshot
import net.bible.sharedcore.settings.store.SettingsStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseAppSettingsTest {
    private class MemoryBackend : SettingsBackend {
        val writes = mutableListOf<SettingWrite>()
        override suspend fun loadAll() = SettingsSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        override suspend fun write(write: SettingWrite) { writes += write }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backend = MemoryBackend()
    private val store = SettingsStore(backend, scope)
    private val notReady: () -> SettingsStore = { throw DataBaseNotReady() }

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun gettersAnswerTheDefaultWhileTheDatabaseIsNotReady() {
        val s = DatabaseAppSettings(notReady) { false }
        assertEquals("d", s.getString("k", "d"))
        assertNull(s.getString("k"))
        assertEquals(7L, s.getLong("k", 7L))
        assertEquals(8, s.getInt("k", 8))
        assertEquals(1.5, s.getDouble("k", 1.5))
        assertEquals(2.5f, s.getFloat("k", 2.5f))
        assertEquals(true, s.getBoolean("k", true)) // answered by the initialized guard, the store is never touched
    }

    @Test
    fun getBooleanAnswersTheDefaultUntilInitializedEvenWhenTheStoreHoldsAValue() {
        store.setBoolean("flag", true)
        var initialized = false
        val s = DatabaseAppSettings({ store }) { initialized }
        assertEquals(false, s.getBoolean("flag", false))
        initialized = true
        assertEquals(true, s.getBoolean("flag", false))
    }

    @Test
    fun otherGettersReadTheStoreBeforeInitialized() {
        store.setString("name", "stored")
        store.setLong("n", 42L)
        val s = DatabaseAppSettings({ store }) { false }
        assertEquals("stored", s.getString("name", "d"))
        assertEquals(42, s.getInt("n", 0))
        assertEquals(42L, s.getLong("n", 0L))
    }

    @Test
    fun settersWriteThroughTheStoreAndConvertNumbers() = runTest {
        val s = DatabaseAppSettings({ store }) { true }
        s.setString("s", "v"); s.setInt("i", 5); s.setLong("l", 6L); s.setBoolean("b", true)
        s.setDouble("d", 1.25); s.setFloat("f", 0.5f)
        store.flush()
        assertEquals("v", store.getString("s", null))
        assertEquals(5L, store.getLong("i", 0L))
        assertEquals(6L, store.getLong("l", 0L))
        assertEquals(true, store.getBoolean("b", false))
        assertEquals(1.25, store.getDouble("d", 0.0))
        assertEquals(0.5, store.getDouble("f", 0.0))
        assertTrue(backend.writes.size == 6)
        s.setInt("i", null); s.setString("s", null)
        store.flush()
        assertEquals(0L, store.getLong("i", 0L))
        assertNull(store.getString("s", null))
    }

    @Test
    fun settersAreNotGuarded() {
        val s = DatabaseAppSettings(notReady) { true }
        assertFailsWith<DataBaseNotReady> { s.setString("k", "v") }
        assertFailsWith<DataBaseNotReady> { s.setBoolean("k", true) }
    }
}
