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

package net.bible.service.sword.epub

import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Optimized EPUB fragments written by released versions (GZIPOutputStream) must stay readable, and vice versa. */
class EpubFragmentGzipCompatTest {
    @get:Rule val tmp = TemporaryFolder()

    private val text = "<div>Äiti – אב</div>"

    @Test fun fragmentWrittenByOldGzipOutputStreamIsReadByNewReader() {
        val file = tmp.newFile("old.frag")
        file.outputStream().use { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }
        assertEquals(text, readFragment(file.toOkioPath()))
    }

    @Test fun fragmentWrittenByNewWriterIsReadByGzipInputStream() {
        val file = tmp.newFile("new.frag")
        writeFragment(file.toOkioPath(), text)
        val read = file.inputStream().use { inp -> GZIPInputStream(inp).use { String(it.readBytes()) } }
        assertEquals(text, read)
        assertEquals(text, readFragment(file.toOkioPath()))
    }
}
