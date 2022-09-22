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

package net.bible.android.view.activity.page;

import net.bible.android.control.page.ChapterVerse;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class ChapterVerseTest {

	@Test
	public void constructor() throws Exception {
		ChapterVerse chapterVerse = ChapterVerse.fromHtmlId("12.34");
		assertThat(chapterVerse.getChapter(), equalTo(12));
		assertThat(chapterVerse.getVerse(), equalTo(34));
	}

	@Test
	public void after() throws Exception {
		assertThat(ChapterVerse.fromHtmlId("12.34").after(ChapterVerse.fromHtmlId("12.33")), is(true));
		assertThat(ChapterVerse.fromHtmlId("12.3").after(ChapterVerse.fromHtmlId("11.33")), is(true));
		assertThat(ChapterVerse.fromHtmlId("12.3").after(ChapterVerse.fromHtmlId("12.3")), is(false));
		assertThat(ChapterVerse.fromHtmlId("12.2").after(ChapterVerse.fromHtmlId("12.3")), is(false));
		assertThat(ChapterVerse.fromHtmlId("11.22").after(ChapterVerse.fromHtmlId("12.3")), is(false));
	}

	@Test
	public void before() throws Exception {
		assertThat(ChapterVerse.fromHtmlId("12.34").before(ChapterVerse.fromHtmlId("12.33")), is(false));
		assertThat(ChapterVerse.fromHtmlId("12.3").before(ChapterVerse.fromHtmlId("11.33")), is(false));
		assertThat(ChapterVerse.fromHtmlId("12.3").before(ChapterVerse.fromHtmlId("12.3")), is(false));
		assertThat(ChapterVerse.fromHtmlId("12.2").before(ChapterVerse.fromHtmlId("12.3")), is(true));
		assertThat(ChapterVerse.fromHtmlId("11.22").before(ChapterVerse.fromHtmlId("12.3")), is(true));
	}
}
