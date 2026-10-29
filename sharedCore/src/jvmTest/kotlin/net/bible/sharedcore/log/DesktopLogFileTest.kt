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
package net.bible.sharedcore.log

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLogFileTest {
    @AfterTest fun stopFileLogging() = DesktopLogFile.close()

    private fun logDir(): Path = Files.createTempDirectory("desktop-log").resolve("logs")

    @Test fun linesGoToTheFileWithLevelTagAndStackTrace() {
        val dir = logDir()
        configureDesktopLogFile(dir)
        Log.w("Tag", "careful")
        Log.e("Tag", "boom", IllegalStateException("kaput"))
        val text = Files.readString(dir.resolve("andbible.log"))
        assertTrue(text.contains("W/Tag: careful"), text)
        assertTrue(text.contains("E/Tag: boom"), text)
        assertTrue(text.contains("IllegalStateException: kaput"), text)
        DesktopLogFile.close()
        Log.w("Tag", "after close")
        assertFalse(Files.readString(dir.resolve("andbible.log")).contains("after close"))
    }

    @Test fun rotatesAtTheLimitKeepingThreeArchives() {
        val dir = logDir()
        DesktopLogFile.configure(dir, maxBytes = 1024, keep = 3)
        repeat(60) { Log.i("Rot", "line-$it-" + "x".repeat(80)) }
        val names = Files.list(dir).use { l -> l.map { it.fileName.toString() }.sorted().toList() }
        assertEquals(listOf("andbible.log", "andbible.log.1", "andbible.log.2", "andbible.log.3"), names)
        for (n in names) assertTrue(Files.size(dir.resolve(n)) <= 1024, "$n is ${Files.size(dir.resolve(n))}")
        // newest archive holds later lines than an older one
        val one = Files.readString(dir.resolve("andbible.log.1")); val three = Files.readString(dir.resolve("andbible.log.3"))
        fun firstIndex(s: String) = Regex("line-(\\d+)-").find(s)!!.groupValues[1].toInt()
        assertTrue(firstIndex(one) > firstIndex(three))
        assertTrue(Files.readString(dir.resolve("andbible.log")).contains("line-59-"))
    }

    @Test fun reopeningAppendsToTheExistingFile() {
        val dir = logDir()
        configureDesktopLogFile(dir); Log.i("A", "first")
        configureDesktopLogFile(dir); Log.i("A", "second")
        val text = Files.readString(dir.resolve("andbible.log"))
        assertTrue(text.indexOf("first") in 0 until text.indexOf("second"))
    }
}
