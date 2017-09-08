package net.bible.android.view.activity.page;

import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class ChapterVerseTest {

	@Test
	public void constructor() throws Exception {
		ChapterVerse chapterVerse = new ChapterVerse("12.34");
		assertThat(chapterVerse.getChapter(), equalTo(12));
		assertThat(chapterVerse.getVerse(), equalTo(34));
	}
}