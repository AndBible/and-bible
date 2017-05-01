package net.bible.service.readingplan;

import net.bible.android.control.versification.TestData;

import org.crosswire.jsword.passage.Key;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class PassageReaderTest {

	private PassageReader passageReader;

	@Before
	public void setup() {
		passageReader = new PassageReader(TestData.KJV);
	}

	/**
	 * various names were use for Song of Songs - check which is correct.
	 */
	@Test
	public void testSongOfSongsChapter() {
		final Key key = passageReader.getKey("Song.8");
		assertThat(key.getCardinality(), greaterThan(10));
	}

	@Test
	public void testSongOfSongsChapters() {
		final Key key = passageReader.getKey("Song.1-Song.3");
		assertThat(key.getCardinality(), greaterThan(30));
	}

	@Test
	public void testSongOfSongsBook() {
		final Key key = passageReader.getKey("Song");
		assertThat(key.getCardinality(), greaterThan(100));
	}
}