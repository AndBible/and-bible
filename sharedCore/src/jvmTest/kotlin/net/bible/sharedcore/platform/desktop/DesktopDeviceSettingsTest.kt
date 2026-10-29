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

import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDeviceSettingsTest {
    private fun file(): Path = Files.createTempDirectory("device-settings").resolve("config/prefs.properties")

    @Test fun valuesSurviveReopen() {
        val f = file()
        DesktopDeviceSettings(f).apply {
            setString("s", "hello = wörld\nline2"); setBoolean("b", true); putLong("l", 1L shl 40); putInt("i", -7)
        }
        val reopened = DesktopDeviceSettings(f)
        assertEquals("hello = wörld\nline2", reopened.getString("s", null))
        assertTrue(reopened.getBoolean("b", false))
        assertEquals(1L shl 40, reopened.getLong("l", 0))
        assertEquals(-7, reopened.getInt("i", 0))
    }

    @Test fun defaultsApplyForMissingOrMalformedValues() {
        val s = DesktopDeviceSettings(file())
        assertEquals("d", s.getString("x", "d"))
        assertTrue(s.getBoolean("x", true))
        s.setString("n", "notanumber")
        assertEquals(5L, s.getLong("n", 5L))
        assertFalse(s.getBoolean("n", false))
    }

    @Test fun removeDeletesTheKey() {
        val f = file()
        val s = DesktopDeviceSettings(f)
        s.setString("k", "v"); s.remove("k")
        assertNull(DesktopDeviceSettings(f).getString("k", null))
    }

    @Test fun writeLeavesNoTempFilesAndAReadableFile() {
        val f = file()
        val s = DesktopDeviceSettings(f)
        repeat(20) { s.putInt("k", it) }
        assertEquals(listOf(f.fileName.toString()), Files.list(f.parent).use { l -> l.map { it.fileName.toString() }.toList() })
        assertEquals(19, DesktopDeviceSettings(f).getInt("k", -1))
    }

    @Test fun corruptFileIsTreatedAsEmptyAndRecovers() {
        val f = file()
        Files.createDirectories(f.parent)
        Files.write(f, byteArrayOf(0x5c, 0x75, 0x78)) // "\ux": malformed unicode escape
        val s = DesktopDeviceSettings(f)
        assertEquals("d", s.getString("a", "d"))
        s.setString("a", "v")
        assertEquals("v", DesktopDeviceSettings(f).getString("a", null))
    }

    @Test fun concurrentWritersFromTwoInstancesLoseNothing() {
        val f = file()
        val a = DesktopDeviceSettings(f)
        val b = DesktopDeviceSettings(f)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val threads = listOf(a to "a", b to "b").map { (s, prefix) ->
            thread { try { repeat(100) { s.putInt("$prefix$it", it) } } catch (t: Throwable) { failures += t } }
        }
        threads.forEach { it.join() }
        assertTrue(failures.isEmpty(), failures.toString())
        val merged = DesktopDeviceSettings(f)
        for (prefix in listOf("a", "b")) for (i in 0 until 100) assertEquals(i, merged.getInt("$prefix$i", -1), "$prefix$i")
    }

    @Test fun observeBooleanReportsInitialValueThenChangesOnly() {
        val s = DesktopDeviceSettings(file())
        val seen = mutableListOf<Boolean>()
        val cancel = s.observeBoolean("flag", false) { seen += it }
        assertEquals(listOf(false), seen)
        s.setBoolean("flag", false)       // no change
        s.putString("other", "x")         // unrelated key
        s.setBoolean("flag", true)
        s.setBoolean("flag", true)        // no change
        s.setBoolean("flag", false)
        assertEquals(listOf(false, true, false), seen)
        cancel()
        s.setBoolean("flag", true)
        assertEquals(listOf(false, true, false), seen)
    }

    @Test fun observeBooleanSeesWritesFromAnotherInstanceOfTheSameFile() {
        val f = file()
        val observed = DesktopDeviceSettings(f)
        val seen = mutableListOf<Boolean>()
        val cancel = observed.observeBoolean("flag", false) { seen += it }
        DesktopDeviceSettings(f).setBoolean("flag", true)
        assertEquals(listOf(false, true), seen)
        assertTrue(observed.getBoolean("flag", false))
        cancel()
    }
}
