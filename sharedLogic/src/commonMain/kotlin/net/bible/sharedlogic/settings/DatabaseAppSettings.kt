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

import net.bible.service.db.DataBaseNotReady
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.store.SettingsStore

/**
 * [AppSettings] over the database-backed [SettingsStore]; the same semantics as Android's
 * `CommonUtils.AndBibleSettings`.
 *
 * [store] is a provider because the store belongs to the current database container (it is replaced on restore).
 * A read before the container exists ([DataBaseNotReady]) answers the default. [getBoolean] is guarded by
 * [initialized] instead and answers the default until app initialisation has finished; the other getters must stay
 * readable before that point (the old-settings-key migration and the screens before the app is initialised read real
 * values). Setters are not guarded: writing before the database exists is a crash, as on Android.
 */
class DatabaseAppSettings(
    private val store: () -> SettingsStore,
    private val initialized: () -> Boolean,
) : AppSettings {
    override fun getString(key: String, default: String?): String? = orDefault(default) { store().getString(key, default) }
    override fun getLong(key: String, default: Long): Long = orDefault(default) { store().getLong(key, default) }
    override fun getInt(key: String, default: Int): Int = orDefault(default) { store().getLong(key, default.toLong()).toInt() }
    override fun getBoolean(key: String, default: Boolean): Boolean = if (initialized()) store().getBoolean(key, default) else default
    override fun getDouble(key: String, default: Double): Double = orDefault(default) { store().getDouble(key, default) }
    override fun getFloat(key: String, default: Float): Float = orDefault(default) { store().getDouble(key, default.toDouble()).toFloat() }

    override fun setString(key: String, value: String?) = store().setString(key, value)
    override fun setLong(key: String, value: Long?) = store().setLong(key, value)
    override fun setInt(key: String, value: Int?) = store().setLong(key, value?.toLong())
    override fun setBoolean(key: String, value: Boolean?) = store().setBoolean(key, value)
    override fun setDouble(key: String, value: Double?) = store().setDouble(key, value)
    override fun setFloat(key: String, value: Float?) = store().setDouble(key, value?.toDouble())

    private inline fun <T> orDefault(default: T, read: () -> T): T = try { read() } catch (e: DataBaseNotReady) { default }
}
