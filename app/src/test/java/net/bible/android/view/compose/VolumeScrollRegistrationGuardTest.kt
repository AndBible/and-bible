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

package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F105 follow-up: every scrolling sharedUi file pages with the volume keys, or says why not.
 *
 * Limit: the scan is per file, so a second unregistered scroller inside a file that already registers
 * one is invisible to it.
 */
class VolumeScrollRegistrationGuardTest {
    private val root = File("../sharedUi/src/commonMain/kotlin")
    private val allow = File("src/test/resources/volume-scroll-allowlist.txt").readLines()
        .map { it.substringBefore(" #").trim() }.filter { it.isNotEmpty() }
    private val scroller = Regex("""\bLazyColumn\(|\bLazyVerticalGrid\(|\.verticalScroll\(""")

    internal fun unregistered(path: String, src: String): Boolean =
        scroller.containsMatchIn(src) && "volumeScrollTarget" !in src && "volumeVerticalScroll" !in src

    @Test fun everyScrollingFileIsRegisteredOrAllowlisted() {
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .filter { unregistered(it.path, it.readText()) }
            .map { it.relativeTo(root).path }
            .filter { it !in allow }.sorted().toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun everyAllowlistEntryExists() = assertEquals(emptyList<String>(), allow.filter { !File(root, it).isFile })

    @Test fun theScanSeesTheSources() = assertTrue(root.isDirectory && root.walkTopDown().any { it.name == "VolumeScroll.kt" })

    @Test fun theGuardCanFail() = assertTrue(unregistered("p", "LazyColumn(state = s) { }"))

    @Test fun aRegisteredFileIsAccepted() = assertFalse(unregistered("p", "LazyColumn(Modifier.volumeScrollTarget(s)) { }"))
}
