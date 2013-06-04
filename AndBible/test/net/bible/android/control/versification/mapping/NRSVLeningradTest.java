package net.bible.android.control.versification.mapping;

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
public class NRSVLeningradTest {

	private static final Versification LENINGRAD_VERSIFICATION = Versifications.instance().getVersification("Leningrad");
	private static final Versification NRSV_VERSIFICATION = Versifications.instance().getVersification("NRSV");
	private NRSVLeningradVersificationMapping underTest;
	
    @Before
    public void setUp() throws Exception
    {
    	underTest = new NRSVLeningradVersificationMapping();
    }
	
	@Test
	public void testDifferentBook() {
		check(BibleBook.GEN, 31, 55, 32, 1);
		check(BibleBook.CHR1, 6, 15, 5, 41);
		check(BibleBook.CHR1, 6, 16, 6, 1);
		// if move above 6:1 in Len then can only map same as 6,1 but there should be no mapping in the other direction 
		checkToNRSV(BibleBook.CHR1, 6, 16, 6, 0);
	}

	@Test
	public void testPsalmOffset() {
		check(BibleBook.PS, 7, 0, 7, 1);
		check(BibleBook.PS, 7, 17, 7, 18);
	}
	
	@Test
	public void testExtraNRSVVerse() {
		//3Jn.1.15, Rev.12.18
		check(BibleBook.JOHN3, 1, 15, 1, 15);
		check(BibleBook.REV, 12, 18, 12, 18);
	}

	@Test
	public void testABVerseExtension() {
//		Ps.11.0=Ps.11.1a
//		Ps.11.1=Ps.11.1b
		check(BibleBook.PS, 11, 0, 11, 1);
		checkToLeningrad(BibleBook.PS, 11, 1, 11, 1);
	}
	
	private void check(BibleBook book, int nrsvChap, int nrsvVerseNo, int synChap, int synVerseNo ) {
		//To Leningrad
		checkToLeningrad(book, nrsvChap, nrsvVerseNo, synChap, synVerseNo ); 

		//To NRSV
		checkToNRSV(book, nrsvChap, nrsvVerseNo, synChap, synVerseNo ); 
	}

	private void checkToLeningrad(BibleBook book, int nrsvChap, int nrsvVerseNo, int synChap, int synVerseNo ) {
		//To Leningrad
		Verse nrsvVerse = new Verse(NRSV_VERSIFICATION, book, nrsvChap, nrsvVerseNo);
		Verse leningradVerse = new Verse(LENINGRAD_VERSIFICATION, book, synChap, synVerseNo);
		
		assertThat("NRSV "+book+" "+nrsvChap+":"+nrsvVerseNo+" should be Leningrad "+book+" "+synChap+":"+synVerseNo, 
				underTest.getMappedVerse(nrsvVerse, LENINGRAD_VERSIFICATION),
				equalTo(leningradVerse)); 
	}
	private void checkToNRSV(BibleBook book, int nrsvChap, int nrsvVerseNo, int synChap, int synVerseNo ) {
		//To Leningrad
		Verse nrsvVerse = new Verse(NRSV_VERSIFICATION, book, nrsvChap, nrsvVerseNo);
		Verse leningradVerse = new Verse(LENINGRAD_VERSIFICATION, book, synChap, synVerseNo);
		
		//To NRSV
		assertEquals("Leningrad "+book+" "+synChap+":"+synVerseNo+" should be NRSV "+book+" "+nrsvChap+":"+nrsvVerseNo, 
				nrsvVerse,
				underTest.getMappedVerse(leningradVerse, NRSV_VERSIFICATION));
	}
}