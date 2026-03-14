/*
 * Copyright (c) 2026-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.mybible

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class MyBibleBookTest {

    @Test
    fun `getConfig should include WordsOfChrist feature when requested`() {
        val config = getConfig(
            initials = "MyBible-Test",
            abbreviation = "Test",
            description = "Test",
            language = "en",
            category = "Biblical Texts",
            hasWordsOfChrist = true,
            moduleFileName = "/tmp/module.SQLite3"
        )

        assertThat(config, containsString("Feature=WordsOfChrist"))
    }

    @Test
    fun `getConfig should not include WordsOfChrist feature by default`() {
        val config = getConfig(
            initials = "MyBible-Test",
            abbreviation = "Test",
            description = "Test",
            language = "en",
            category = "Biblical Texts",
            moduleFileName = "/tmp/module.SQLite3"
        )

        assertThat(config, not(containsString("Feature=WordsOfChrist")))
    }
}
