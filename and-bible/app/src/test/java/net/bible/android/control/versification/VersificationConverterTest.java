package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class VersificationConverterTest {

	private VersificationConverter versificationConverter;

	private final Verse SEGOND_JOHN_3_16 = new Verse(TestData.SEGOND, BibleBook.JOHN, 3, 16);

	@Before
	public void setup() {
		versificationConverter = new VersificationConverter();
	}

	@Test
	public void isConvertibleTo() throws Exception {
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.KJV), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.NRSV), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.GERMAN), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.LUTHER), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.KJVA), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.SYNODAL), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.SYNODAL_PROT), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.VULGATE), is(true));
		// contain no NT books
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.MT), is(false));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, TestData.LENINGRAD), is(false));
	}

}