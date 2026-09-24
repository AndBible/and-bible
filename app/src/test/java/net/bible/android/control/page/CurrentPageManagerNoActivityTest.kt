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
package net.bible.android.control.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Slice 8 F5 (slice 7 Task 12): switching to a document whose page has no key opens the key chooser on the
 * CURRENT Activity -- and with none in front it must not crash on `currentActivity!!`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CurrentPageManagerNoActivityTest {

    private lateinit var windowRepository: WindowRepository

    /**
     * `TestDict` is not one of the modules provisioned in `~/.sword` for the unit suite (see
     * `docs`/memory on `.local/testmods.zip`), so -- mirroring
     * `ModuleBackupRoundTripTest.registerMinimalRawLdDictionary` / `AndroidInstallCommitterTest` --
     * this fabricates the smallest real SWORD module (a RawLD dictionary) directly on disk and
     * registers it, rather than depending on any externally-provisioned fixture.
     */
    @Before
    fun setUp() {
        registerMinimalRawLdDictionary()
    }

    @After
    fun tearDown() {
        Books.installed().getBook("TestDict")?.let { Books.installed().removeBook(it) }
        val downloadDir = SwordBookPath.getSwordDownloadDir()
        File(downloadDir, "mods.d/testdict.conf").delete()
        File(downloadDir, "modules/lexdict/rawld/testdict").deleteRecursively()
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun registerMinimalRawLdDictionary(): Book {
        val downloadDir = SwordBookPath.getSwordDownloadDir()
        val conf = """
            [TestDict]
            DataPath=./modules/lexdict/rawld/testdict/test
            ModDrv=RawLD
            SourceType=Plaintext
            Encoding=UTF-8
            Lang=en
            Description=Test Dictionary
            DistributionLicense=Public Domain
        """.trimIndent()

        val confFile = File(downloadDir, "mods.d/testdict.conf")
        confFile.parentFile!!.mkdirs()
        confFile.writeText(conf)

        val dataDir = File(downloadDir, "modules/lexdict/rawld/testdict").apply { mkdirs() }
        val datBytes = "strong\nThe test definition body.".toByteArray(Charsets.UTF_8)
        File(dataDir, "test.dat").writeBytes(datBytes)
        val idxBytes = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putShort(datBytes.size.toShort()).array()
        File(dataDir, "test.idx").writeBytes(idxBytes)

        val bmd = SwordBookMetaData(confFile, NetUtil.getURI(downloadDir))
        bmd.driver = SwordBookDriver.instance()
        SwordBookDriver.registerNewBook(bmd)
        return requireNotNull(Books.installed().getBook("TestDict")) { "TestDict test module must be installed" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    @Test
    fun aKeylessSwitchWithNoActivityInFrontDoesNotCrash() {
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        val dictionary = requireNotNull(Books.installed().getBook("TestDict")) { "TestDict test module must be installed" }
        val pageManager = CommonUtils.windowControl.activeWindowPageManager
        val saved = ArrayList(holderActivities())
        holderActivities().clear()
        try {
            pageManager.setCurrentDocument(dictionary)   // threw NullPointerException on `currentActivity!!`
            assertEquals("TestDict", pageManager.currentPage.currentDocument?.initials)
        } finally {
            holderActivities().addAll(saved)
        }
    }
}
