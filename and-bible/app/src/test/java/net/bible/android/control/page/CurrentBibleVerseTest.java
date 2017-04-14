package net.bible.android.control.page;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class CurrentBibleVerseTest {

	private CurrentBibleVerse currentBibleVerse;
	private Versification synodalV11n = Versifications.instance().getVersification("Synodal");
	private Versification kjvV11n = Versifications.instance().getVersification("KJV");
	private Verse synodalPs9v22 = new Verse(synodalV11n, BibleBook.PS, 9, 22);
	private Verse kjvPs10v1 = new Verse(kjvV11n, BibleBook.PS, 10, 1);
	
	@Before
	public void setUp() throws Exception {
		currentBibleVerse = new CurrentBibleVerse();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetCurrentBibleBookNo() throws Exception {
		currentBibleVerse.setVerseSelected(kjvV11n, kjvPs10v1);
		assertThat(currentBibleVerse.getCurrentBibleBookNo(), equalTo(BibleBook.PS.ordinal()));
	}

	@Test
	public void testGetCurrentBibleBook() throws Exception {
		currentBibleVerse.setVerseSelected(kjvV11n, kjvPs10v1);
		assertThat(currentBibleVerse.getCurrentBibleBook(), equalTo(BibleBook.PS));
		
		currentBibleVerse.setVerseSelected(synodalV11n, kjvPs10v1);
		assertThat(currentBibleVerse.getCurrentBibleBook(), equalTo(BibleBook.PS));
	}

	@Test
	public void testGetVerseSelected() throws Exception {
		currentBibleVerse.setVerseSelected(kjvV11n, kjvPs10v1);
		assertThat(currentBibleVerse.getVerseSelected(synodalV11n), equalTo(synodalPs9v22));
		assertThat(currentBibleVerse.getVerseSelected(kjvV11n), equalTo(kjvPs10v1));

		currentBibleVerse.setVerseSelected(synodalV11n, kjvPs10v1);
		assertThat(currentBibleVerse.getVerseSelected(synodalV11n), equalTo(synodalPs9v22));
		assertThat(currentBibleVerse.getVerseSelected(kjvV11n), equalTo(kjvPs10v1));
	}
}
