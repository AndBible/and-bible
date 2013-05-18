package net.bible.android.control.versification.mapping;

import static org.junit.Assert.assertEquals;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class KJVSynodalTest {

	private static final Versification SYNODAL_VERSIFICATION = Versifications.instance().getVersification("Synodal");
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification("KJV");
	private KJVSynodalVersificationMapping testItem;
	
    @Before
    public void setUp() throws Exception
    {
    	testItem = new KJVSynodalVersificationMapping();
    }
	
	@Test
	public void testNotMovedPsalm() {
		check(BibleBook.PS, 1, 2, 1, 2);
	}

	@Test
	public void testSimpleMovedPsalm() {
		check(BibleBook.PS, 11, 2, 10, 2);
	}

	@Test
	public void testBorderCase() {
		check(BibleBook.PS, 9, 20, 9, 21);
		check(BibleBook.PS, 10, 1, 9, 22);
	}
	
	private void check(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Synodal
		Verse kjvVerse = new Verse(KJV_VERSIFICATION, BibleBook.PS, kjvChap, kjvVerseNo);
		Verse synodalVerse = new Verse(SYNODAL_VERSIFICATION, BibleBook.PS, synChap, synVerseNo);
		
		assertEquals("KJV "+book+" "+kjvChap+":"+kjvVerse+" should be Synodal "+book+" "+synChap+":"+synVerseNo, 
				synodalVerse,
				testItem.getMappedVerse(kjvVerse, SYNODAL_VERSIFICATION)); 

		//To KJV
		assertEquals("Synodal "+book+" "+synChap+":"+synVerseNo+" should be KJV "+book+" "+kjvChap+":"+kjvVerseNo, 
				kjvVerse,
				testItem.getMappedVerse(synodalVerse, SYNODAL_VERSIFICATION));
	}
}