/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.common

import junit.framework.Assert
import net.bible.test.DatabaseResetter.resetDatabase
import net.bible.service.common.FileManager.copyFile
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import net.bible.test.DatabaseResetter
import kotlin.Throws
import org.robolectric.shadows.ShadowStatFs
import net.bible.service.common.FileManager
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File
import java.lang.Exception

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileManagerTest {
    private val folder = "src/test/resources/net/bible/service/common".replace("/", File.separator)
    @After
    fun tearDown() {
        resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun shouldCopyFile() {
        // ensure Android thinks there is enough room
        ShadowStatFs.registerStats(folder, 100, 20, 10)
        val toCopy = File(folder, "testFileToCopy")
        val target = File(folder, "copiedFile")
        target.deleteOnExit()
        Assert.assertTrue("copy failed", copyFile(toCopy, target))
        Assert.assertTrue(target.exists())
    }

    @Test
    @Throws(Exception::class)
    fun shouldOverwriteOnCopyIfTargetFileExists() {
        // ensure Android thinks there is enough room
        ShadowStatFs.registerStats(folder, 100, 20, 10)
        val toCopy = File(folder, "testFileToCopy")
        val target = File(folder, "copiedFile")
        target.deleteOnExit()
        Assert.assertTrue("initial copy failed", copyFile(toCopy, target))
        Assert.assertTrue("overwriting copy failed", copyFile(toCopy, target))
        Assert.assertEquals("copied file has different length", toCopy.length(), target.length())
        Assert.assertTrue(target.exists())
    }
}
