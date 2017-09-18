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