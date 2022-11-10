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

import net.bible.service.download.FakeBookFactory.createFakeRepoBook
import org.crosswire.jsword.book.Book
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class RepoBookDeduplicatorTest {
    private var repoBookDeduplicator: RepoBookDeduplicator? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        repoBookDeduplicator = RepoBookDeduplicator()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
    }

    @Test
    @Throws(Exception::class)
    fun testAdd1() {
        val svBook = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null)
        val books = Arrays.asList(svBook)
        repoBookDeduplicator!!.addAll(books)
        Assert.assertThat<List<Book>>(repoBookDeduplicator!!.books, Matchers.contains(svBook))
    }

    @Test
    @Throws(Exception::class)
    fun testAddNewer() {
        val svBook = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null)
        val books1 = Arrays.asList(svBook)
        repoBookDeduplicator!!.addAll(books1)
        val svBookNewer = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.1", null)
        val books2 = Arrays.asList(svBookNewer)
        repoBookDeduplicator!!.addAll(books2)
        Assert.assertThat<List<Book>>(repoBookDeduplicator!!.books, Matchers.contains(svBookNewer))
        Assert.assertThat(repoBookDeduplicator!!.books[0].getProperty("Version"), CoreMatchers.equalTo("1.0.1"))
    }

    @Test
    @Throws(Exception::class)
    fun testAddOlder() {
        val svBook = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.1", null)
        val books1 = Arrays.asList(svBook)
        repoBookDeduplicator!!.addAll(books1)
        val svBookOlder = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null)
        val books2 = Arrays.asList(svBookOlder)
        repoBookDeduplicator!!.addAll(books2)
        Assert.assertThat<List<Book>>(repoBookDeduplicator!!.books, Matchers.contains(svBook))
        Assert.assertThat(repoBookDeduplicator!!.books[0].getProperty("Version"), CoreMatchers.equalTo("1.0.1"))
    }

    @Test
    @Throws(Exception::class)
    fun testAddIfNotExists() {
        val svBook = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null)
        val books1 = Arrays.asList(svBook)
        repoBookDeduplicator!!.addAll(books1)
        repoBookDeduplicator!!.addIfNotExists(books1)
        Assert.assertThat<List<Book>>(repoBookDeduplicator!!.books, Matchers.contains(svBook))
        Assert.assertThat(repoBookDeduplicator!!.books[0].getProperty("Version"), CoreMatchers.equalTo("1.0.0"))
    }

    @Test
    @Throws(Exception::class)
    fun testAddIfNotExistsNewer() {
        val svBook = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null)
        val books1 = Arrays.asList(svBook)
        repoBookDeduplicator!!.addAll(books1)
        val svBookNewer = createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.1", null)
        val books2 = Arrays.asList(svBookNewer)
        repoBookDeduplicator!!.addIfNotExists(books2)
        Assert.assertThat<List<Book>>(repoBookDeduplicator!!.books, Matchers.contains(svBook))
        Assert.assertThat(repoBookDeduplicator!!.books[0].getProperty("Version"), CoreMatchers.equalTo("1.0.0"))
    }
}
