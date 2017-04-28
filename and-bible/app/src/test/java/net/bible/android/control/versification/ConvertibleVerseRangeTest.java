package net.bible.android.control.versification;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

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
}