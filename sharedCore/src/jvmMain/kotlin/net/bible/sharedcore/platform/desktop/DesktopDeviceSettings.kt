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
package net.bible.sharedcore.platform.desktop

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.settings.DeviceLocalPrefs
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Device-local preferences (the desktop counterpart of Android's `SharedPreferences` and `IosDeviceSettings`) in one
 * `java.util.Properties` file, normally `<config>/device-settings.properties`. They are read before the database exists.
 *
 * Instances for the same file share one in-memory copy, loaded once. Every write updates it and replaces the file
 * atomically (a temp file in the same directory, then a move) under one process-wide lock, so writers on several threads
 * or several instances lose nothing and a crash never leaves a half-written file. Changes made to the file by another
 * process are not picked up while the app runs (the desktop app is single-instance). Values are stored as text.
 */
class DesktopDeviceSettings(file: Path) : DeviceLocalPrefs {
    private val file: Path = file.toAbsolutePath().normalize()
    private val state: State = synchronized(LOCK) { STATES.getOrPut(this.file) { State(readFile()) } }

    override fun getString(key: String, default: String?): String? = getString(key) ?: default
    override fun setString(key: String, value: String) = putString(key, value)
    override fun setBoolean(key: String, value: Boolean) = putBoolean(key, value)
    override fun remove(key: String) = putString(key, null)

    fun getString(key: String): String? = synchronized(LOCK) { state.values[key] }

    /** A null [value] removes the key. */
    fun putString(key: String, value: String?) {
        val changed = synchronized(LOCK) {
            val current = state.values.toMutableMap()
            val before = current[key]
            if (value == null) current.remove(key) else current[key] = value
            if (before != value) {
                try {
                    writeFile(current)
                } catch (e: IOException) {
                    // A preference write must not crash its caller; the value stays in memory for this run.
                    Log.w(TAG, "Could not persist device settings to $file", e)
                }
            }
            state.values = current
            before != value
        }
        if (changed) LISTENERS.forEach { it() }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key)?.let { it.toBooleanStrictOrNull() } ?: default

    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    fun getLong(key: String, default: Long): Long = getString(key)?.toLongOrNull() ?: default
    fun putLong(key: String, value: Long) = putString(key, value.toString())
    fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default
    fun putInt(key: String, value: Int) = putString(key, value.toString())

    /**
     * Calls [onChange] with the current value now and whenever a write by any [DesktopDeviceSettings] in this process
     * changes it. The returned function stops the observation.
     */
    fun observeBoolean(key: String, default: Boolean, onChange: (Boolean) -> Unit): () -> Unit {
        var previous = getBoolean(key, default)
        val listener: () -> Unit = {
            val value = getBoolean(key, default)
            val fire = synchronized(this) { (value != previous).also { if (it) previous = value } }
            if (fire) onChange(value)
        }
        LISTENERS += listener
        onChange(previous)
        return { LISTENERS -= listener }
    }

    private fun readFile(): Map<String, String> {
        if (!Files.exists(file)) return emptyMap()
        return try {
            val props = Properties()
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { props.load(it) }
            props.stringPropertyNames().associateWith { props.getProperty(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable device settings $file, treating as empty", e)
            emptyMap()
        }
    }

    private fun writeFile(values: Map<String, String>) {
        val directory = file.toAbsolutePath().parent
        Files.createDirectories(directory)
        val temp = Files.createTempFile(directory, file.fileName.toString(), ".tmp")
        try {
            val props = Properties().apply { values.forEach { (k, v) -> setProperty(k, v) } }
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use { props.store(it, null) }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private class State(@Volatile var values: Map<String, String>)

    internal companion object {
        /** Test hook: forgets the shared in-memory copies so the next open reads the file again. */
        internal fun clearSharedStateForTest() = synchronized(LOCK) { STATES.clear() }

        private const val TAG = "DesktopDeviceSettings"
        private val LOCK = Any()
        private val STATES = HashMap<Path, State>()
        private val LISTENERS = CopyOnWriteArrayList<() -> Unit>()
    }
}
