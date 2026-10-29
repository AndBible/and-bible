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
package net.bible.sharedui.host.ports

import okio.Buffer
import okio.IOException
import okio.buffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InstallPipelineIoJvmTest {
    private val dir = Files.createTempDirectory("install-io-test")
    private val io = JvmInstallPipelineIo(temp = { dir })

    @Test fun tempDirIsTheInjectedDirectory() {
        assertEquals(dir.toString(), io.tempDir().toString())
    }

    @Test fun jobIdsAreUnique() {
        assertNotEquals(io.newJobId(), io.newJobId())
    }

    @Test fun filePathDecodesPercentEscapesAndKeepsLiteralBackslashes() {
        assertEquals("/tmp/a b/c.zip", io.filePath("file:///tmp/a%20b/c.zip"))
        assertEquals("/tmp/ä.zip", io.filePath("file:///tmp/%C3%A4.zip"))
        // A literal backslash is part of a Unix file name, not a separator and not an illegal URL character.
        assertEquals("/tmp/we\\ird.zip", io.filePath("file:///tmp/we\\ird.zip"))
        assertEquals("/tmp/x.zip", io.filePath("file://localhost/tmp/x.zip"))
    }

    @Test fun filePathRefusesAnythingButAnAbsoluteFileUrl() {
        listOf("/tmp/x.zip", "relative/x.zip", "http://example.org/x.zip", "file:relative.zip", "content://x/y", "file:///tmp/bad%zz.zip", "")
            .forEach { uri -> assertFailsWith<IOException>(uri) { io.filePath(uri) } }
    }

    @Test fun openSourceStreamsTheExactFileAndCloses() {
        val file = dir.resolve("it's here.bin")
        val bytes = ByteArray(20_000) { (it % 251).toByte() }
        Files.write(file, bytes)
        val uri = "file://" + file.toString().replace(" ", "%20")
        val buffer = Buffer()
        io.openSource(uri).buffer().use { it.readAll(buffer) }
        assertEquals(bytes.toList(), buffer.readByteArray().toList())
    }

    @Test fun openSourceOfAMissingFileIsAnIoException() {
        assertFailsWith<IOException> { io.openSource("file://" + dir.resolve("missing.zip")) }
    }

    @Test fun sleepWaitsAtLeastTheRequestedTime() {
        val start = System.nanoTime()
        io.sleepMs(30)
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 25)
    }

    @Test fun theProductionActualUsesTheSystemTempDirectory() {
        val real = installPipelineIo()
        assertEquals(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).toString(), real.tempDir().toString())
    }
}
