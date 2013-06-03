package net.bible.android.control.versification.mapping;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import junit.framework.TestCase;

import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

public class VersificationMappingFactoryTest extends TestCase {

	private VersificationMappingFactory underTest;
	
	private static final Versification SYNODAL_VERSIFICATION = Versifications.instance().getVersification("Synodal");
	private static final Versification LENINGRAD_VERSIFICATION = Versifications.instance().getVersification("Leningrad");
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification("KJV");
	
	protected void setUp() throws Exception {
		underTest = VersificationMappingFactory.getInstance();
	}

	public void testGetLeningradVersificationMapping() {
		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVLeningradMapping"));
		assertThat(underTest.getVersificationMapping(KJV_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("KJVLeningradMapping"));
	}

	public void testGetSynodalVersificationMapping() {
		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVSynodalMapping"));
		assertThat(underTest.getVersificationMapping(KJV_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("KJVSynodalMapping"));
	}

	public void testGetNoVersificationMapping() {
		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("NoVersificationMapping"));
		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("NoVersificationMapping"));
	}
}
