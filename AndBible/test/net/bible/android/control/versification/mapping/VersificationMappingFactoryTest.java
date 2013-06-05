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
	private static final Versification GERMAN_VERSIFICATION = Versifications.instance().getVersification("German");
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification("KJV");
	private static final Versification NRSV_VERSIFICATION = Versifications.instance().getVersification("NRSV");
	private static final Versification VULG_VERSIFICATION = Versifications.instance().getVersification("Vulg");
	private static final Versification CATHOLIC_VERSIFICATION = Versifications.instance().getVersification("Catholic");
	
	protected void setUp() throws Exception {
		underTest = VersificationMappingFactory.getInstance();
	}

	public void testGetLeningradVersificationMapping() {
		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVLeningradMapping"));
		assertThat(underTest.getVersificationMapping(KJV_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("KJVLeningradMapping"));
		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, NRSV_VERSIFICATION).toString(), equalTo("NRSVLeningradMapping"));
		assertThat(underTest.getVersificationMapping(NRSV_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("NRSVLeningradMapping"));
		assertThat(underTest.getVersificationMapping(GERMAN_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("GermanLeningradMapping"));
		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, VULG_VERSIFICATION).toString(), equalTo("LeningradVulgMapping"));
	}

	public void testGetSynodalVersificationMapping() {
		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVSynodalMapping"));
		assertThat(underTest.getVersificationMapping(KJV_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("KJVSynodalMapping"));
		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, NRSV_VERSIFICATION).toString(), equalTo("NRSVSynodalMapping"));
		assertThat(underTest.getVersificationMapping(NRSV_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("NRSVSynodalMapping"));
		assertThat(underTest.getVersificationMapping(VULG_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("SynodalVulgMapping"));
	}

	public void testGetGermanVersificationMapping() {
		assertThat(underTest.getVersificationMapping(GERMAN_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVGermanMapping"));
		assertThat(underTest.getVersificationMapping(GERMAN_VERSIFICATION, NRSV_VERSIFICATION).toString(), equalTo("NRSVGermanMapping"));
	}

	public void testGetNoVersificationMapping() {
		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("NoVersificationMapping"));
		assertThat(underTest.getVersificationMapping(VULG_VERSIFICATION, CATHOLIC_VERSIFICATION).toString(), equalTo("NoVersificationMapping"));
	}
}
