package net.bible.android.control.versification.sort;

import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.android.control.versification.TestData;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class VersificationPrioritiserTest {

	@Test
	public void prioritiseVersifications() {
		final List<ConvertibleVerseRangeUser> convertibleVerseRangeUsers = Arrays.asList(
				createConvertibleVerseRangeUserWith(TestData.KJV),
				createConvertibleVerseRangeUserWith(TestData.NRSV),
				createConvertibleVerseRangeUserWith(TestData.LXX),
				createConvertibleVerseRangeUserWith(TestData.NRSV),
				createConvertibleVerseRangeUserWith(TestData.KJV),
				createConvertibleVerseRangeUserWith(TestData.NRSV),
				createConvertibleVerseRangeUserWith(TestData.SEGOND)
		);

		VersificationPrioritiser versificationPrioritiser = new VersificationPrioritiser(convertibleVerseRangeUsers);

		final List<Versification> orderedVersifications = versificationPrioritiser.getPrioritisedVersifications();

		assertThat(orderedVersifications.get(0), equalTo(TestData.NRSV));
		assertThat(orderedVersifications.get(1), equalTo(TestData.KJV));
		assertThat(orderedVersifications.size(), equalTo(4));
	}

	private ConvertibleVerseRangeUser createConvertibleVerseRangeUserWith(final Versification v11n) {
		return new ConvertibleVerseRangeUser() {
			@Override
			public ConvertibleVerseRange getConvertibleVerseRange() {
				return new ConvertibleVerseRange(new VerseRange(v11n, new Verse(v11n, BibleBook.JOHN, 3, 16)));
			}
		};
	}
}