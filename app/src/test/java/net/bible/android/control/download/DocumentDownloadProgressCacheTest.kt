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

package net.bible.android.control.download

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import net.bible.android.activity.R
import net.bible.android.activity.databinding.DocumentListItemBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import net.bible.android.view.activity.download.DocumentListItem
import net.bible.service.download.FakeBookFactory
import net.bible.test.DatabaseResetter
import org.crosswire.common.progress.JobManager
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DocumentDownloadProgressCacheTest {
    private var documentDownloadProgressCache: DocumentDownloadProgressCache? = null
    private var testData: TestData? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        documentDownloadProgressCache = DocumentDownloadProgressCache()
        testData = TestData()
    }

    @Test
    @Throws(InterruptedException::class)
    fun sendEventOnProgress() {
        val eventReceiver = EventReceiver()
        ABEventBus.getDefault().register(eventReceiver)
        documentDownloadProgressCache!!.startMonitoringDownloads()
        testData!!.progress.workDone = 30
        Thread.sleep(10)
        MatcherAssert.assertThat(eventReceiver.received, Matchers.`is`(true))
    }

    @After
    fun tearDown() {
        ABEventBus.getDefault().unregisterAll()
        DatabaseResetter.resetDatabase()
    }

    class EventReceiver {
        var received = false
        fun onEvent(event: DocumentDownloadEvent?) {
            received = true
        }
    }

    private inner class TestData {
        var initials = "KJV"
        var document: Book? = null
        var progress = JobManager.createJob("INSTALL_BOOK-$initials", "Installing King James Version", null)
        var documentDownloadListItem: DocumentListItem? = null
        var progressBar: ProgressBar? = null

        init {
            try {
                document = FakeBookFactory.createFakeRepoBook(initials, "[KJV]\nDescription=My Test Book", "")
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: BookException) {
                e.printStackTrace()
            }
            progress.totalWork = 100
            progress.work = 33
            val activity = Robolectric.buildActivity(Activity::class.java).create().get()
            documentDownloadListItem = (LayoutInflater.from(activity).inflate(R.layout.document_list_item, null) as DocumentListItem).also {
                it.binding = DocumentListItemBinding.inflate(activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            }

            documentDownloadListItem!!.document = document!!
            progressBar = documentDownloadListItem!!.binding.progressBar
        }
    }
}
