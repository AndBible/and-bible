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
public class GermanSynodalTest {

	private static final Versification GERMAN_VERSIFICATION = Versifications.instance().getVersification("German");
	private static final Versification SYNODAL_VERSIFICATION = Versifications.instance().getVersification("Synodal");
	private GermanSynodalVersificationMapping underTest;
	
    @Before
    public void setUp() throws Exception
    {
    	underTest = new GermanSynodalVersificationMapping();
    }
	
	@Test
	public void testOnlyGermanMapping() {
		//KJVToGerman: Gen.31.55=Gen.32.1
		check(BibleBook.GEN, 32, 1, 31, 55);
	}
	@Test
	public void testOnlySynodalMapping() {
		//KJVToSynodal: Lev.14.57=Lev.14.56
		check(BibleBook.LEV, 14, 57, 14, 56);
	}

	@Test
	public void testDoubleMapping() {
		//KJVToGerman: 
//			Ps.3.1=Ps.3.2
		//KJVToSynodal: 
//			Ps.3.1=Ps.3.2
		check(BibleBook.PS, 3, 2, 3, 2);
		
		//KJVToGerman: 
//			Ps.21.10=Ps.21.11
		//KJVToSynodal: 
//			Ps.21.10=Ps.20.11
		check(BibleBook.PS, 21, 11, 20, 11);
	}

	private void check(BibleBook book, int germanChap, int germanVerseNo, int synChap, int synVerseNo ) {
		checkToSynodal(book, germanChap, germanVerseNo, synChap, synVerseNo ); 

		checkToGerman(book, germanChap, germanVerseNo, synChap, synVerseNo ); 
	}

	private void checkToSynodal(BibleBook book, int germanChap, int germanVerseNo, int synChap, int synVerseNo ) {
		Verse germanVerse = new Verse(GERMAN_VERSIFICATION, book, germanChap, germanVerseNo);
		Verse synodalVerse = new Verse(SYNODAL_VERSIFICATION, book, synChap, synVerseNo);
		
		assertThat("German "+book+" "+germanChap+":"+germanVerseNo+" should be Synodal "+book+" "+synChap+":"+synVerseNo, 
				underTest.getMappedVerse(germanVerse, SYNODAL_VERSIFICATION),
				equalTo(synodalVerse)); 
	}
	private void checkToGerman(BibleBook book, int germanChap, int germanVerseNo, int synChap, int synVerseNo ) {
		Verse germanVerse = new Verse(GERMAN_VERSIFICATION, book, germanChap, germanVerseNo);
		Verse synodalVerse = new Verse(SYNODAL_VERSIFICATION, book, synChap, synVerseNo);
		
		assertEquals("Synodal "+book+" "+synChap+":"+synVerseNo+" should be German "+book+" "+germanChap+":"+germanVerseNo, 
				germanVerse,
				underTest.getMappedVerse(synodalVerse, GERMAN_VERSIFICATION));
	}
}