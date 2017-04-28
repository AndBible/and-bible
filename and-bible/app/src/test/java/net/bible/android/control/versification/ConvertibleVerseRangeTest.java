package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.SystemKJVA;
import org.crosswire.jsword.versification.system.SystemSynodalProt;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class ConvertibleVerseRangeTest {

	@Test
	public void testGetVerseRange() throws Exception {
		ConvertibleVerseRange convertibleVerseRange = new ConvertibleVerseRange(TestData.KJV_PS_14_2_4);
		assertThat(convertibleVerseRange.getVerseRange(), equalTo(TestData.KJV_PS_14_2_4));
	}

	@Test
	public void testGetVerseRangeWithDifferentVersification() throws Exception {
		ConvertibleVerseRange convertibleVerseRange = new ConvertibleVerseRange(TestData.KJV_PS_14_2_4);
		assertThat(convertibleVerseRange.getVerseRange(TestData.SYNODAL_PROT), equalTo(TestData.SYN_PROT_PS_13_2_4));
	}

	@Test
	public void testcompareToEqualDifferentVersification() throws Exception {
		ConvertibleVerseRange convertibleVerseRange1 = new ConvertibleVerseRange(TestData.KJV_PS_14_2_4);
		ConvertibleVerseRange convertibleVerseRange2 = new ConvertibleVerseRange(TestData.SYN_PROT_PS_13_2_4);

		assertThat(convertibleVerseRange1.compareTo(convertibleVerseRange2), equalTo(0));
		assertThat(convertibleVerseRange2.compareTo(convertibleVerseRange1), equalTo(0));
	}

	@Test
	public void testcompareToDeuterocanonicalDifferentVersification() throws Exception {
		ConvertibleVerseRange convertibleVerseRange1 = new ConvertibleVerseRange(TestData.KJVA_1MACC_1_2_3);
		ConvertibleVerseRange convertibleVerseRange2 = new ConvertibleVerseRange(TestData.SYN_PROT_PS_13_2_4);

		assertThat(convertibleVerseRange1.compareTo(convertibleVerseRange2), greaterThan(0));
		assertThat(convertibleVerseRange2.compareTo(convertibleVerseRange1), lessThan(0));
	}

	@Test
	public void testSort() throws Exception {
		ConvertibleVerseRange convertibleVerseRange1 = new ConvertibleVerseRange(TestData.KJVA_1MACC_1_2_3);
		ConvertibleVerseRange convertibleVerseRange2 = new ConvertibleVerseRange(TestData.SYN_PROT_PS_13_2_4);

		List<ConvertibleVerseRange> verseRanges = Arrays.asList(convertibleVerseRange1, convertibleVerseRange2);

		Collections.sort(verseRanges);

		assertThat(verseRanges, contains(convertibleVerseRange2, convertibleVerseRange1));
	}

	interface TestData {
		Versification KJV = Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		Versification KJVA = Versifications.instance().getVersification(SystemKJVA.V11N_NAME);
		Versification SYNODAL_PROT = Versifications.instance().getVersification(SystemSynodalProt.V11N_NAME);
		// these verses should be equivalent
		Verse KJV_PS_14_2 = new Verse(KJV, BibleBook.PS, 14, 2);
		Verse KJV_PS_14_4 = new Verse(KJV, BibleBook.PS, 14, 4);
		VerseRange KJV_PS_14_2_4 = new VerseRange(KJV, KJV_PS_14_2, KJV_PS_14_4);

		Verse SYN_PROT_PS_13_2 = new Verse(SYNODAL_PROT, BibleBook.PS, 13, 2);
		Verse SYN_PROT_PS_13_4 = new Verse(SYNODAL_PROT, BibleBook.PS, 13, 4);
		VerseRange SYN_PROT_PS_13_2_4 = new VerseRange(SYNODAL_PROT, SYN_PROT_PS_13_2, SYN_PROT_PS_13_4);

		Verse KJVA_1MACCPS_1_2 = new Verse(KJVA, BibleBook.MACC1, 1, 2);
		Verse KJVA_1MACCPS_1_3 = new Verse(KJVA, BibleBook.MACC1, 1, 3);
		VerseRange KJVA_1MACC_1_2_3 = new VerseRange(KJVA, KJVA_1MACCPS_1_2, KJVA_1MACCPS_1_3);
	}
}