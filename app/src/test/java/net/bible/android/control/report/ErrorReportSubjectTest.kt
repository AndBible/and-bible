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
package net.bible.android.control.report

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bug report subject names the version and the crash location (#4160). Release builds are R8-obfuscated, so
 * AndBible's frames no longer contain "net.bible": the subject must still carry the version and an (obfuscated)
 * location that `scripts/retrace-crash.sh` can map back.
 */
class ErrorReportSubjectTest {
    private val version = "6.0.1277-beta#7c618d9975"
    private val mapId = "r8-map-id-a2ce5146482e6c1fea67ebc0f86b412fce8873df34e9ae05d65183f4bdb639a9"

    private fun throwable(message: String?, vararg frames: StackTraceElement) =
        IllegalStateException(message).apply { stackTrace = arrayOf(*frames) }

    @Test
    fun unobfuscatedBuildNamesTheFirstAndBibleFrame() {
        val e = throwable(
            "boom",
            StackTraceElement("kotlinx.coroutines.DispatchedTask", "run", "DispatchedTask.kt", 100),
            StackTraceElement("net.bible.android.control.page.CurrentPageBase", "getPageContent", "CurrentPageBase.kt", 199),
        )
        assertEquals("$version boom:net.bible.android.control.page.CurrentPageBase.getPageContent:199",
            errorReportSubject(version, e))
    }

    @Test
    fun obfuscatedBuildKeepsTheVersionAndNamesTheFirstObfuscatedFrame() {
        val e = throwable(
            "John 3:16 was not found",
            StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 959),
            StackTraceElement("t58", "j", mapId, 234),
            StackTraceElement("q32", "u", mapId, 238),
        )
        assertEquals("$version John 3:16 was not found:t58.j:234", errorReportSubject(version, e))
    }

    @Test
    fun obfuscatedFrameBeforeAKeptNameFrameWins() {
        // A class that kept its name (activity, $$serializer, Parcelable) can sit further down the stack than the
        // real, obfuscated crash frame; the first of the two kinds is the location.
        val e = throwable(
            "boom",
            StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 959),
            StackTraceElement("t58", "j", mapId, 234),
            StackTraceElement("net.bible.android.view.activity.MainActivity", "onCreate", "MainActivity.kt", 50),
        )
        assertEquals("$version boom:t58.j:234", errorReportSubject(version, e))
    }

    @Test
    fun noKnownFrameStillNamesTheVersionAndTheTopFrame() {
        val e = throwable(null, StackTraceElement("android.os.Looper", "loop", "Looper.java", 1), )
        assertEquals("$version java.lang.IllegalStateException:android.os.Looper.loop:1", errorReportSubject(version, e))
    }

    @Test
    fun withoutAnExceptionTheSubjectIsTheVersion() {
        assertEquals(version, errorReportSubject(version, null))
        assertEquals(version, errorReportSubject(version, throwable("x")))
    }
}
