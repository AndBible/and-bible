package net.bible.android.view.activity.page;

import net.bible.android.control.page.ChapterVerse;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class ChapterVerseTest {

	@Test
	public void constructor() throws Exception {
		ChapterVerse chapterVerse = new ChapterVerse("12.34");
		assertThat(chapterVerse.getChapter(), equalTo(12));
		assertThat(chapterVerse.getVerse(), equalTo(34));
	}

	@Test
	public void after() throws Exception {
		assertThat(new ChapterVerse("12.34").after(new ChapterVerse("12.33")), is(true));
		assertThat(new ChapterVerse("12.3").after(new ChapterVerse("11.33")), is(true));
		assertThat(new ChapterVerse("12.3").after(new ChapterVerse("12.3")), is(false));
		assertThat(new ChapterVerse("12.2").after(new ChapterVerse("12.3")), is(false));
		assertThat(new ChapterVerse("11.22").after(new ChapterVerse("12.3")), is(false));
	}

	@Test
	public void before() throws Exception {
		assertThat(new ChapterVerse("12.34").before(new ChapterVerse("12.33")), is(false));
		assertThat(new ChapterVerse("12.3").before(new ChapterVerse("11.33")), is(false));
		assertThat(new ChapterVerse("12.3").before(new ChapterVerse("12.3")), is(false));
		assertThat(new ChapterVerse("12.2").before(new ChapterVerse("12.3")), is(true));
		assertThat(new ChapterVerse("11.22").before(new ChapterVerse("12.3")), is(true));
	}
}