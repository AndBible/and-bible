package net.bible.android.control.versification.sort;

import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.android.control.versification.TestData;
import net.bible.service.db.bookmark.BookmarkDto;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class ConvertibleVerseRangeComparatorTest {

	private ConvertibleVerseRangeComparator convertibleVerseRangeComparator;
	
	@Before
	public void setup() {
		BookmarkDto bookmarkDto = new BookmarkDto();
		bookmarkDto.setVerseRange(TestData.SYN_PROT_PS_13_2_4);
		BookmarkDto bookmarkDto2 = new BookmarkDto();
		bookmarkDto2.setVerseRange(TestData.KJVA_1MACC_1_2_3);
		convertibleVerseRangeComparator = new ConvertibleVerseRangeComparator.Builder().withBookmarks(Arrays.asList(bookmarkDto, bookmarkDto2)).build();
	}
	
	@Test
	public void testcompareToEqualDifferentVersification() throws Exception {
		ConvertibleVerseRangeUser convertibleVerseRangeUser1 = createConvertibleVerseRangeUserWith(new ConvertibleVerseRange(TestData.KJV_PS_14_2_4));
		ConvertibleVerseRangeUser convertibleVerseRangeUser2 = createConvertibleVerseRangeUserWith(new ConvertibleVerseRange(TestData.SYN_PROT_PS_13_2_4));

		assertThat(convertibleVerseRangeComparator.compare(convertibleVerseRangeUser1, convertibleVerseRangeUser2), equalTo(0));
		assertThat(convertibleVerseRangeComparator.compare(convertibleVerseRangeUser2, convertibleVerseRangeUser1), equalTo(0));
	}

	@Test
	public void testcompareToDeuterocanonicalDifferentVersification() throws Exception {
		ConvertibleVerseRangeUser convertibleVerseRangeUser1 = createConvertibleVerseRangeUserWith(new ConvertibleVerseRange(TestData.KJVA_1MACC_1_2_3));
		ConvertibleVerseRangeUser convertibleVerseRangeUser2 = createConvertibleVerseRangeUserWith(new ConvertibleVerseRange(TestData.SYN_PROT_PS_13_2_4));

		assertThat(convertibleVerseRangeComparator.compare(convertibleVerseRangeUser1, convertibleVerseRangeUser2), greaterThan(0));
		assertThat(convertibleVerseRangeComparator.compare(convertibleVerseRangeUser2, convertibleVerseRangeUser1), lessThan(0));
	}

	@Test
	public void testSort() throws Exception {
		ConvertibleVerseRangeUser convertibleVerseRangeUser1 = createConvertibleVerseRangeUserWith(new ConvertibleVerseRange(TestData.KJVA_1MACC_1_2_3));
		ConvertibleVerseRangeUser convertibleVerseRangeUser2 = createConvertibleVerseRangeUserWith(new ConvertibleVerseRange(TestData.SYN_PROT_PS_13_2_4));

		List<ConvertibleVerseRangeUser> verseRanges = Arrays.asList(convertibleVerseRangeUser1, convertibleVerseRangeUser2);

		Collections.sort(verseRanges, convertibleVerseRangeComparator);

		assertThat(verseRanges, contains(convertibleVerseRangeUser2, convertibleVerseRangeUser1));
	}

	private ConvertibleVerseRangeUser createConvertibleVerseRangeUserWith(final ConvertibleVerseRange convertibleVerseRange) {
		return new ConvertibleVerseRangeUser() {
			@Override
			public ConvertibleVerseRange getConvertibleVerseRange() {
				return convertibleVerseRange;
			}
		};
	}
}