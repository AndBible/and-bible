package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.SystemSynodalProt;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
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

	interface TestData {
		Versification KJV = Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		Versification SYNODAL_PROT = Versifications.instance().getVersification(SystemSynodalProt.V11N_NAME);
		// these verses should be equivalent
		Verse KJV_PS_14_2 = new Verse(KJV, BibleBook.PS, 14, 2);
		Verse KJV_PS_14_4 = new Verse(KJV, BibleBook.PS, 14, 4);
		VerseRange KJV_PS_14_2_4 = new VerseRange(KJV, KJV_PS_14_2, KJV_PS_14_4);

		Verse SYN_PROT_PS_13_2 = new Verse(SYNODAL_PROT, BibleBook.PS, 13, 2);
		Verse SYN_PROT_PS_13_4 = new Verse(SYNODAL_PROT, BibleBook.PS, 13, 4);
		VerseRange SYN_PROT_PS_13_2_4 = new VerseRange(SYNODAL_PROT, SYN_PROT_PS_13_2, SYN_PROT_PS_13_4);
	}
}