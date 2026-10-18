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
package net.bible.sharedlogic.startup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `BookType.fromString` returns the FIRST match, so the common test cannot see a duplicate registration. This JVM
 * test reads the registry list itself (reflection on the private companion field) and counts occurrences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedAppStartupBookTypeRegistryTest {
    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()); SharedAppStartup.resetForTest() }

    @AfterTest
    fun tearDown() { SharedAppStartup.resetForTest(); Dispatchers.resetMain() }

    @Suppress("UNCHECKED_CAST")
    private fun registry(): List<BookType> {
        val field = BookType::class.java.getDeclaredField("supportedBookTypes").apply { isAccessible = true }
        return (field.get(null) as List<BookType>).toList()
    }

    @Test
    fun repeated_installEnvironment_registers_each_extra_book_type_exactly_once() {
        val provider = LocaleProviderManager.getLocaleProvider()
        SharedAppStartup.installEnvironment(PropertyMap(), provider)
        SharedAppStartup.installEnvironment(PropertyMap(), provider)
        val extras = listOf(
            net.bible.service.sword.mybible.myBibleBible, net.bible.service.sword.mybible.myBibleCommentary,
            net.bible.service.sword.mybible.myBibleDictionary, net.bible.service.sword.mysword.mySwordBible,
            net.bible.service.sword.mysword.mySwordCommentary, net.bible.service.sword.mysword.mySwordDictionary,
            net.bible.service.sword.epub.epubBookType, net.bible.service.sword.esword.eSwordBible,
        )
        val types = registry()
        extras.forEach { e -> assertEquals(1, types.count { it === e }, "$e must be registered exactly once") }
        val names = types.map { it.toString() }
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals(emptyMap(), duplicates, "book types registered more than once: $duplicates")
        assertEquals(13 + 8, names.size, "13 built-in plus 8 extra types: $names")
    }
}
