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
package net.bible.android.view.activity.page

import net.bible.android.control.page.ChapterVerse.Companion.fromHtmlId
import kotlin.Throws
import org.hamcrest.core.IsEqual
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import java.lang.Exception

class ChapterVerseTest {
    @Test
    @Throws(Exception::class)
    fun constructor() {
        val (chapter, verse) = fromHtmlId("12.34")
        Assert.assertThat(chapter, IsEqual.equalTo(12))
        Assert.assertThat(verse, IsEqual.equalTo(34))
    }

    @Test
    @Throws(Exception::class)
    fun after() {
        Assert.assertThat(fromHtmlId("12.34").after(fromHtmlId("12.33")), Matchers.`is`(true))
        Assert.assertThat(fromHtmlId("12.3").after(fromHtmlId("11.33")), Matchers.`is`(true))
        Assert.assertThat(fromHtmlId("12.3").after(fromHtmlId("12.3")), Matchers.`is`(false))
        Assert.assertThat(fromHtmlId("12.2").after(fromHtmlId("12.3")), Matchers.`is`(false))
        Assert.assertThat(fromHtmlId("11.22").after(fromHtmlId("12.3")), Matchers.`is`(false))
    }

    @Test
    @Throws(Exception::class)
    fun before() {
        Assert.assertThat(fromHtmlId("12.34").before(fromHtmlId("12.33")), Matchers.`is`(false))
        Assert.assertThat(fromHtmlId("12.3").before(fromHtmlId("11.33")), Matchers.`is`(false))
        Assert.assertThat(fromHtmlId("12.3").before(fromHtmlId("12.3")), Matchers.`is`(false))
        Assert.assertThat(fromHtmlId("12.2").before(fromHtmlId("12.3")), Matchers.`is`(true))
        Assert.assertThat(fromHtmlId("11.22").before(fromHtmlId("12.3")), Matchers.`is`(true))
    }
}
