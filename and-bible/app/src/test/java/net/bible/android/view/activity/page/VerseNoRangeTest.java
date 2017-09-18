package net.bible.android.view.activity.page;

import android.support.annotation.NonNull;

import net.bible.android.control.page.ChapterVerse;

import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class VerseNoRangeTest {

	private VerseNoRange verseNoRange;

	@Before
	public void setup() {
	}

	@Test
	public void testClone() throws Exception {
		VerseNoRange verseNoRange = getVerseNoRange(4, 7);
		VerseNoRange clone = verseNoRange.clone();
		assertThat(clone, equalTo(verseNoRange));
	}

	@Test
	public void testExpandDown() throws Exception {
		verseNoRange = getVerseNoRange(7, 7);
		verseNoRange.alter(getChapterVerse(10));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(7)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(10)));
	}

	@Test
	public void testExpandDown_differentChapter() throws Exception {
		verseNoRange = getVerseNoRange(7, 7);
		verseNoRange.alter(getChapterVerse(8, 10));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(7)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(8, 10)));
	}

	@Test
	public void testExpandUp() throws Exception {
		verseNoRange = getVerseNoRange(7, 7);
		verseNoRange.alter(getChapterVerse(3));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(3)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(7)));
	}

	@Test
	public void testExpandUp_differentChapter() throws Exception {
		verseNoRange = getVerseNoRange(7, 7);
		verseNoRange.alter(getChapterVerse(2, 13));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(2, 13)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(7)));
	}

	@Test
	public void testReduceUp() throws Exception {
		verseNoRange = getVerseNoRange(3, 7);
		verseNoRange.alter(getChapterVerse(6));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(3)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(5)));
	}

	@Test
	public void testReduceUp_differentChapter() throws Exception {
		verseNoRange = getVerseNoRange(3, 3, 4, 7);
		verseNoRange.alter(getChapterVerse(3, 6));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(3)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(5)));

		verseNoRange.alter(getChapterVerse(4, 7));
		verseNoRange.alter(getChapterVerse(4, 6));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(4, 5)));
	}

	@Test
	public void testReduceDown() throws Exception {
		verseNoRange = getVerseNoRange(3, 7);
		verseNoRange.alter(getChapterVerse(3));
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(4)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(7)));
	}

	@Test
	public void testReduceDown_differentChapter() throws Exception {
		verseNoRange = getVerseNoRange(3, 3, 4, 7);
		verseNoRange.alter(getChapterVerse(3, 3));
		// there is a compromise in the code that prevents the first verse being deselected if multiple chapters in selection
		assertThat(verseNoRange.getStart(), equalTo(getChapterVerse(3, 3)));
		assertThat(verseNoRange.getEnd(), equalTo(getChapterVerse(4, 7)));
	}

	@Test
	public void testReduceToZero() throws Exception {
		verseNoRange = getVerseNoRange(3, 3);
		verseNoRange.alter(getChapterVerse(3));
		assertThat(verseNoRange.isEmpty(), equalTo(true));
		assertThat(verseNoRange.getStart(), equalTo(ChapterVerse.NO_VALUE));
		assertThat(verseNoRange.getEnd(), equalTo(ChapterVerse.NO_VALUE));
	}

	@Test
	public void testGetExtras() {
		verseNoRange = getVerseNoRange(3, 7);
		VerseNoRange other = getVerseNoRange(6, 8);

		assertThat(verseNoRange.getExtrasIn(other), containsInAnyOrder(getChapterVerse(8)));
		assertThat(other.getExtrasIn(verseNoRange), containsInAnyOrder(getChapterVerse(3),getChapterVerse(4),getChapterVerse(5)));
	}

	@Test
	public void testGetExtras_multipleChapters() {
		verseNoRange = getVerseNoRange(3, 13, 4, 3);
		VerseNoRange other = getVerseNoRange(3, 12, 4, 5);

		assertThat(verseNoRange.getExtrasIn(other), containsInAnyOrder(getChapterVerse(3,12), getChapterVerse(4, 4), getChapterVerse(4, 5)));
		assertThat(other.getExtrasIn(verseNoRange), containsInAnyOrder());
	}

	@NonNull
	private VerseNoRange getVerseNoRange(int startVerse, int endVerse) {
		ChapterVerse start = getChapterVerse(startVerse);
		ChapterVerse end = getChapterVerse(endVerse);
		return new VerseNoRange(TestData.V11N, BibleBook.JOHN, start, end);
	}

	@NonNull
	private VerseNoRange getVerseNoRange(int startChapter, int startVerse, int endChapter, int endVerse) {
		ChapterVerse start = getChapterVerse(startChapter, startVerse);
		ChapterVerse end = getChapterVerse(endChapter, endVerse);
		return new VerseNoRange(TestData.V11N, BibleBook.JOHN, start, end);
	}

	@NonNull
	private ChapterVerse getChapterVerse(int verse) {
		return new ChapterVerse(TestData.CHAPTER, verse);
	}

	@NonNull
	private ChapterVerse getChapterVerse(int chapter, int verse) {
		return new ChapterVerse(chapter, verse);
	}

	private interface TestData {
		Versification V11N = Versifications.instance().getVersification(Versifications.DEFAULT_V11N);
		int CHAPTER = 3;
	}
}