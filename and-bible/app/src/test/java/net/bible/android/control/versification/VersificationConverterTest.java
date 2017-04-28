package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class VersificationConverterTest {

	private VersificationConverter versificationConverter;

	private final Versification kjv = Versifications.instance().getVersification("KJV");
	private final Versification nrsv = Versifications.instance().getVersification("NRSV");
	private final Versification mt = Versifications.instance().getVersification("MT");
	private final Versification segond = Versifications.instance().getVersification("Segond");
	private final Versification german = Versifications.instance().getVersification("German");
	private final Versification leningrad = Versifications.instance().getVersification("Leningrad");
	private final Versification luther = Versifications.instance().getVersification("Luther");
	private final Versification kjva = Versifications.instance().getVersification("KJVA");
	private final Versification synodal = Versifications.instance().getVersification("Synodal");
	private final Versification synodalProt = Versifications.instance().getVersification("SynodalProt");

	private final Verse SEGOND_JOHN_3_16 = new Verse(segond, BibleBook.JOHN, 3, 16);

	@Before
	public void setup() {
		versificationConverter = new VersificationConverter();
	}

	@Test
	public void isConvertibleTo() throws Exception {
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, kjv), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, nrsv), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, german), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, luther), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, kjva), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, synodal), is(true));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, synodalProt), is(true));
		// contain no NT books
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, mt), is(false));
		assertThat(versificationConverter.isConvertibleTo(SEGOND_JOHN_3_16, leningrad), is(false));
	}

}