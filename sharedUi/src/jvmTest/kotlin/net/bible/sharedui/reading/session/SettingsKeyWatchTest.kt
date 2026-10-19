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

package net.bible.sharedui.reading.session

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.settings.store.SettingWrite
import net.bible.sharedcore.settings.store.SettingsBackend
import net.bible.sharedcore.settings.store.SettingsSnapshot
import net.bible.sharedcore.settings.store.SettingsStore
import net.bible.sharedlogic.settings.DatabaseAppSettings

/** Writes through the real [DatabaseAppSettings] over a [SettingsStore] reach the watchers with no platform event. */
class SettingsKeyWatchTest {
    private class MemoryBackend : SettingsBackend {
        override suspend fun loadAll() = SettingsSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        override suspend fun write(write: SettingWrite) = Unit
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store = SettingsStore(MemoryBackend(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    private val settings = DatabaseAppSettings({ store }) { true }

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun colourModeAndAnimationWritesNotifyOnlyForWatchedKeys() {
        var hits = 0
        watchSettingKeys(store.changes, setOf("display_color_mode", "disable_animations"), scope) { hits++ }
        settings.setString("display_color_mode", "MONOCHROME")
        settings.setBoolean("disable_animations", true)
        settings.setBoolean("unrelated", true)
        assertEquals(2, hits)
    }

    @Test
    fun keepScreenOnFollowsTheDatabasePreferenceWithoutAnAppActiveEvent() {
        val applied = mutableListOf<Boolean>()
        val keepOn = ReadingKeepScreenOn(read = { settings.getBoolean("screen_keep_on_pref", false) }, apply = { applied += it })
        keepOn.setReading(true)
        watchSettingKeys(store.changes, setOf("screen_keep_on_pref"), scope) { keepOn.refresh() }
        settings.setBoolean("screen_keep_on_pref", true)
        assertEquals(listOf(false, true), applied)
        settings.setBoolean("screen_keep_on_pref", false)
        assertEquals(listOf(false, true, false), applied)
    }
}
