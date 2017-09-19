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
public class ChapterVerseRangeTest {

	private ChapterVerseRange chapterVerseRange;

	@Before
	public void setup() {
	}

	@Test
	public void testExpandDown() throws Exception {
		chapterVerseRange = getChapterVerseRange(7, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(10));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(7)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(10)));
	}

	@Test
	public void testExpandDown_differentChapter() throws Exception {
		chapterVerseRange = getChapterVerseRange(7, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(8, 10));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(7)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(8, 10)));
	}

	@Test
	public void testExpandUp() throws Exception {
		chapterVerseRange = getChapterVerseRange(7, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(3));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(3)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(7)));
	}

	@Test
	public void testExpandUp_differentChapter() throws Exception {
		chapterVerseRange = getChapterVerseRange(7, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(2, 13));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(2, 13)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(7)));
	}

	@Test
	public void testReduceUp() throws Exception {
		chapterVerseRange = getChapterVerseRange(3, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(6));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(3)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(5)));
	}

	@Test
	public void testReduceUp_differentChapter() throws Exception {
		chapterVerseRange = getChapterVerseRange(3, 3, 4, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(3, 6));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(3)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(5)));

		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(4, 7));
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(4, 6));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(4, 5)));
	}

	@Test
	public void testReduceDown() throws Exception {
		chapterVerseRange = getChapterVerseRange(3, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(3));
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(4)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(7)));
	}

	@Test
	public void testReduceDown_differentChapter() throws Exception {
		chapterVerseRange = getChapterVerseRange(3, 3, 4, 7);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(3, 3));
		// there is a compromise in the code that prevents the first verse being deselected if multiple chapters in selection
		assertThat(chapterVerseRange.getStart(), equalTo(getChapterVerse(3, 3)));
		assertThat(chapterVerseRange.getEnd(), equalTo(getChapterVerse(4, 7)));
	}

	@Test
	public void testReduceToZero() throws Exception {
		chapterVerseRange = getChapterVerseRange(3, 3);
		chapterVerseRange = chapterVerseRange.toggleVerse(getChapterVerse(3));
		assertThat(chapterVerseRange.isEmpty(), equalTo(true));
		assertThat(chapterVerseRange.getStart(), equalTo(ChapterVerse.Companion.getNOT_SET()));
		assertThat(chapterVerseRange.getEnd(), equalTo(ChapterVerse.Companion.getNOT_SET()));
	}

	@Test
	public void testGetExtras() {
		chapterVerseRange = getChapterVerseRange(3, 7);
		ChapterVerseRange other = getChapterVerseRange(6, 8);

		assertThat(chapterVerseRange.getExtrasIn(other), containsInAnyOrder(getChapterVerse(8)));
		assertThat(other.getExtrasIn(chapterVerseRange), containsInAnyOrder(getChapterVerse(3),getChapterVerse(4),getChapterVerse(5)));
	}

	@Test
	public void testGetExtras_multipleChapters() {
		chapterVerseRange = getChapterVerseRange(3, 13, 4, 3);
		ChapterVerseRange other = getChapterVerseRange(3, 12, 4, 5);

		assertThat(chapterVerseRange.getExtrasIn(other), containsInAnyOrder(getChapterVerse(3,12), getChapterVerse(4, 4), getChapterVerse(4, 5)));
		assertThat(other.getExtrasIn(chapterVerseRange), containsInAnyOrder());
	}

	@NonNull
	private ChapterVerseRange getChapterVerseRange(int startVerse, int endVerse) {
		ChapterVerse start = getChapterVerse(startVerse);
		ChapterVerse end = getChapterVerse(endVerse);
		return new ChapterVerseRange(TestData.V11N, BibleBook.JOHN, start, end);
	}

	@NonNull
	private ChapterVerseRange getChapterVerseRange(int startChapter, int startVerse, int endChapter, int endVerse) {
		ChapterVerse start = getChapterVerse(startChapter, startVerse);
		ChapterVerse end = getChapterVerse(endChapter, endVerse);
		return new ChapterVerseRange(TestData.V11N, BibleBook.JOHN, start, end);
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