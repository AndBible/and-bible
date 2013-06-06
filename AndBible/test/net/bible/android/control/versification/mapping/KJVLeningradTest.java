package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.LENINGRAD_V11N;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class KJVLeningradTest {

	private static final Versification LENINGRAD_VERSIFICATION = Versifications.instance().getVersification(LENINGRAD_V11N);
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification(KJV_V11N);
	private KJVLeningradVersificationMapping underTest;
	
    @Before
    public void setUp() throws Exception
    {
    	underTest = new KJVLeningradVersificationMapping();
    }
	
	@Test
	public void testDifferentBook() {
		check(BibleBook.GEN, 31, 55, 32, 1);
		check(BibleBook.CHR1, 6, 15, 5, 41);
		check(BibleBook.CHR1, 6, 16, 6, 1);
		// if move above 6:1 in Len then can only map same as 6,1 but there should be no mapping in the other direction 
		checkToKJV(BibleBook.CHR1, 6, 16, 6, 0);
	}

	@Test
	public void testPsalmOffset() {
		check(BibleBook.PS, 7, 0, 7, 1);
		check(BibleBook.PS, 7, 17, 7, 18);
	}
	
	@Test
	public void testABVerseExtension() {
//		Ps.11.0=Ps.11.1a
//		Ps.11.1=Ps.11.1b
		check(BibleBook.PS, 11, 0, 11, 1);
		checkToLeningrad(BibleBook.PS, 11, 1, 11, 1);
	}
	
	private void check(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Leningrad
		checkToLeningrad(book, kjvChap, kjvVerseNo, synChap, synVerseNo ); 

		//To KJV
		checkToKJV(book, kjvChap, kjvVerseNo, synChap, synVerseNo ); 
	}

	private void checkToLeningrad(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Leningrad
		Verse kjvVerse = new Verse(KJV_VERSIFICATION, book, kjvChap, kjvVerseNo);
		Verse leningradVerse = new Verse(LENINGRAD_VERSIFICATION, book, synChap, synVerseNo);
		
		assertThat("KJV "+book+" "+kjvChap+":"+kjvVerseNo+" should be Leningrad "+book+" "+synChap+":"+synVerseNo, 
				underTest.getMappedVerse(kjvVerse, LENINGRAD_VERSIFICATION),
				equalTo(leningradVerse)); 
	}
	private void checkToKJV(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Leningrad
		Verse kjvVerse = new Verse(KJV_VERSIFICATION, book, kjvChap, kjvVerseNo);
		Verse leningradVerse = new Verse(LENINGRAD_VERSIFICATION, book, synChap, synVerseNo);
		
		//To KJV
		assertEquals("Leningrad "+book+" "+synChap+":"+synVerseNo+" should be KJV "+book+" "+kjvChap+":"+kjvVerseNo, 
				kjvVerse,
				underTest.getMappedVerse(leningradVerse, KJV_VERSIFICATION));
	}
}