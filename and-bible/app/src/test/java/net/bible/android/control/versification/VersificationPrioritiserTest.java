package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class VersificationPrioritiserTest {

	@Test
	public void prioritiseVersifications() {
		final Versification kjv = Versifications.instance().getVersification("KJV");
		final Versification nrsv = Versifications.instance().getVersification("NRSV");
		final Versification lxx = Versifications.instance().getVersification("LXX");
		final Versification segond = Versifications.instance().getVersification("Segond");
		final List<ConvertibleVerseRangeUser> convertibleVerseRangeUsers = Arrays.asList(
				createConvertibleVerseRangeWith(kjv),
				createConvertibleVerseRangeWith(nrsv),
				createConvertibleVerseRangeWith(lxx),
				createConvertibleVerseRangeWith(nrsv),
				createConvertibleVerseRangeWith(kjv),
				createConvertibleVerseRangeWith(nrsv),
				createConvertibleVerseRangeWith(segond)
		);

		VersificationPrioritiser versificationPrioritiser = new VersificationPrioritiser(convertibleVerseRangeUsers);

		final List<Versification> orderedVersifications = versificationPrioritiser.getPrioritisedVersifications();

		assertThat(orderedVersifications.get(0), equalTo(nrsv));
		assertThat(orderedVersifications.get(1), equalTo(kjv));
	}

	private ConvertibleVerseRangeUser createConvertibleVerseRangeWith(final Versification v11n) {
		return new ConvertibleVerseRangeUser() {
			@Override
			public ConvertibleVerseRange getConvertibleVerseRange() {
				return new ConvertibleVerseRange(new VerseRange(v11n, new Verse(v11n, BibleBook.JOHN, 3, 16)));
			}
		};
	}
}