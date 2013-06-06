package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.SYNODAL_V11N;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.android.control.versification.mapping.base.PropertyFileVersificationMapping;

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

	private static final Versification SYNODAL_VERSIFICATION = Versifications.instance().getVersification(SYNODAL_V11N);
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification(KJV_V11N);
	private PropertyFileVersificationMapping testItem;
	
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
	
	@Test
	public void testVerse0Case() {
		// Num.12.16=Num.13.1
		// Num.13.1=Num.13.2

		// Rule: If there is no mapping rule for verse 0 but there is for verse 1 then use that rule but it can be different for both directions
		check(BibleBook.NUM, 12, 16, 13, 1);
		checkToSynodal(BibleBook.NUM, 13, 0, 13, 2); // i.e. if user scrolls just above verse 1 in KJV module
		checkToKJV(BibleBook.NUM, 12, 16, 13, 0); // i.e. if user scrolls just above verse 1 in Synodal module
		check(BibleBook.NUM, 13, 1, 13, 2);
	}

	@Test
	public void testABVerseExtension() {
//		Ps.17.0=Ps.16.1a
//		Ps.17.1=Ps.16.1b
		check(BibleBook.PS, 17, 0, 16, 1);
		checkToSynodal(BibleBook.PS, 17, 1, 16, 1);
	}
	
	private void check(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Synodal
		checkToSynodal(book, kjvChap, kjvVerseNo, synChap, synVerseNo ); 

		//To KJV
		checkToKJV(book, kjvChap, kjvVerseNo, synChap, synVerseNo ); 
	}

	private void checkToSynodal(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Synodal
		Verse kjvVerse = new Verse(KJV_VERSIFICATION, book, kjvChap, kjvVerseNo);
		Verse synodalVerse = new Verse(SYNODAL_VERSIFICATION, book, synChap, synVerseNo);
		
		assertThat("KJV "+book+" "+kjvChap+":"+kjvVerseNo+" should be Synodal "+book+" "+synChap+":"+synVerseNo, 
				testItem.getMappedVerse(kjvVerse, SYNODAL_VERSIFICATION),
				equalTo(synodalVerse)); 
	}
	private void checkToKJV(BibleBook book, int kjvChap, int kjvVerseNo, int synChap, int synVerseNo ) {
		//To Synodal
		Verse kjvVerse = new Verse(KJV_VERSIFICATION, book, kjvChap, kjvVerseNo);
		Verse synodalVerse = new Verse(SYNODAL_VERSIFICATION, book, synChap, synVerseNo);
		
		//To KJV
		assertThat("Synodal "+book+" "+synChap+":"+synVerseNo+" should be KJV "+book+" "+kjvChap+":"+kjvVerseNo, 
				testItem.getMappedVerse(synodalVerse, KJV_VERSIFICATION),
				equalTo(kjvVerse));
	}
}