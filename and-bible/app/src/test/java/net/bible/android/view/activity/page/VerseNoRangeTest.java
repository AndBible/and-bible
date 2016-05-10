package net.bible.android.view.activity.page;

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
		VerseNoRange verseNoRange = new VerseNoRange(4,7);
		VerseNoRange clone = verseNoRange.clone();
		assertThat(clone, equalTo(verseNoRange));
	}

	@Test
	public void testExpandDown() throws Exception {
		verseNoRange = new VerseNoRange(7);
		verseNoRange.alter(10);
		assertThat(verseNoRange.getStartVerseNo(), equalTo(7));
		assertThat(verseNoRange.getEndVerseNo(), equalTo(10));
	}

	@Test
	public void testExpandUp() throws Exception {
		verseNoRange = new VerseNoRange(7);
		verseNoRange.alter(3);
		assertThat(verseNoRange.getStartVerseNo(), equalTo(3));
		assertThat(verseNoRange.getEndVerseNo(), equalTo(7));
	}

	@Test
	public void testReduceUp() throws Exception {
		verseNoRange = new VerseNoRange(3, 7);
		verseNoRange.alter(6);
		assertThat(verseNoRange.getStartVerseNo(), equalTo(3));
		assertThat(verseNoRange.getEndVerseNo(), equalTo(5));
	}

	@Test
	public void testReduceDown() throws Exception {
		verseNoRange = new VerseNoRange(3, 7);
		verseNoRange.alter(3);
		assertThat(verseNoRange.getStartVerseNo(), equalTo(4));
		assertThat(verseNoRange.getEndVerseNo(), equalTo(7));
	}

	@Test
	public void testReduceToZero() throws Exception {
		verseNoRange = new VerseNoRange(3, 3);
		verseNoRange.alter(3);
		assertThat(verseNoRange.isEmpty(), equalTo(true));
		assertThat(verseNoRange.getStartVerseNo(), equalTo(-1));
		assertThat(verseNoRange.getEndVerseNo(), equalTo(-1));
	}

	@Test
	public void testGetExtras() {
		verseNoRange = new VerseNoRange(3, 7);
		VerseNoRange other = new VerseNoRange(6, 8);

		assertThat(verseNoRange.getExtrasIn(other), containsInAnyOrder(8));
		assertThat(other.getExtrasIn(verseNoRange), containsInAnyOrder(3,4,5));

	}
}