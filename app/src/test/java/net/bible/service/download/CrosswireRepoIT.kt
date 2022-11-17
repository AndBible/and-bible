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

package net.bible.service.download

import net.bible.android.TEST_SDK
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.InstallException
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Ignore("Test documents need to be downloaded separately (see .travis.yml)")
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[TEST_SDK])
class CrosswireRepoIT {

    private lateinit var crosswireRepo: CrosswireRepo

    @Before
    @Throws(Exception::class)
    fun setUp() {
        crosswireRepo = CrosswireRepo()
        val repoFactory = RepoFactory(DownloadManager(null))
        crosswireRepo.repoFactory = repoFactory
    }

    @Test
    @Throws(InstallException::class)
    fun getRepoBooks() {
        val repoBooks = crosswireRepo.getRepoBooks(true)
        assertThat(repoBooks.size, greaterThan(100))
    }

    @Test
    fun getRepoName() {
        assertThat(crosswireRepo.repoName, equalTo("CrossWire"))
    }

    @Test
    fun downloadDocument() {
        val repoBooks = crosswireRepo.getRepoBooks(false)
        val kjv = repoBooks.findLast { it.initials == "KJV" }
        print(kjv)
        crosswireRepo.downloadDocument(kjv!!)
        assertThat(Books.installed().getBook("KJV"), not(nullValue()))
    }

    @Test
    fun downloadDocumentsRequiredForTests() {
        // Let's remove ESV until it comes back to crosswire's repository
        //val testBooks = arrayOf("KJV", "ISV", "ESV2011", "FinRK", "FinPR", "FinSTLK2017")
        val testBooks = arrayOf("KJV", "ISV", "FinRK", "FinPR", "FinSTLK2017")
        crosswireRepo.getRepoBooks(false)
                .filter {testBooks.contains(it.initials)}
                .filter {Books.installed().getBook(it.initials) == null}
                .forEach {crosswireRepo.downloadDocument(it)}
        assertThat(Books.installed().books.size, greaterThanOrEqualTo(testBooks.size))
    }
}
