package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.GERMAN_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.LENINGRAD_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.NRSV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.SYNODAL_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.VULG_V11N;
import junit.framework.TestCase;

import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

public class VersificationMappingFactoryTest extends TestCase {

	private VersificationMappingFactory underTest;
	
	private static final Versification SYNODAL_VERSIFICATION = Versifications.instance().getVersification(SYNODAL_V11N);
	private static final Versification LENINGRAD_VERSIFICATION = Versifications.instance().getVersification(LENINGRAD_V11N);
	private static final Versification GERMAN_VERSIFICATION = Versifications.instance().getVersification(GERMAN_V11N);
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification(KJV_V11N);
	private static final Versification NRSV_VERSIFICATION = Versifications.instance().getVersification(NRSV_V11N);
	private static final Versification VULG_VERSIFICATION = Versifications.instance().getVersification(VULG_V11N);
	private static final Versification CATHOLIC_VERSIFICATION = Versifications.instance().getVersification("Catholic");
	
	protected void setUp() throws Exception {
		underTest = VersificationMappingFactory.getInstance();
	}

//TODO pre-initialization dependency on Sword causes problems so when Guice integrated prevent initialization or extract out
//	public void testGetLeningradVersificationMapping() {
//		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVLeningradMapping"));
//		assertThat(underTest.getVersificationMapping(KJV_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("KJVLeningradMapping"));
//		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, NRSV_VERSIFICATION).toString(), equalTo("NRSVLeningradMapping"));
//		assertThat(underTest.getVersificationMapping(NRSV_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("NRSVLeningradMapping"));
//		assertThat(underTest.getVersificationMapping(GERMAN_VERSIFICATION, LENINGRAD_VERSIFICATION).toString(), equalTo("GermanLeningradMapping"));
//		assertThat(underTest.getVersificationMapping(LENINGRAD_VERSIFICATION, VULG_VERSIFICATION).toString(), equalTo("LeningradVulgMapping"));
//	}
//
//	public void testGetSynodalVersificationMapping() {
//		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVSynodalMapping"));
//		assertThat(underTest.getVersificationMapping(KJV_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("KJVSynodalMapping"));
//		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, NRSV_VERSIFICATION).toString(), equalTo("NRSVSynodalMapping"));
//		assertThat(underTest.getVersificationMapping(NRSV_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("NRSVSynodalMapping"));
//		assertThat(underTest.getVersificationMapping(VULG_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("SynodalVulgMapping"));
//	}
//
//	public void testGetGermanVersificationMapping() {
//		assertThat(underTest.getVersificationMapping(GERMAN_VERSIFICATION, KJV_VERSIFICATION).toString(), equalTo("KJVGermanMapping"));
//		assertThat(underTest.getVersificationMapping(GERMAN_VERSIFICATION, NRSV_VERSIFICATION).toString(), equalTo("NRSVGermanMapping"));
//	}
//
//	public void testGetNoVersificationMapping() {
//		assertThat(underTest.getVersificationMapping(SYNODAL_VERSIFICATION, SYNODAL_VERSIFICATION).toString(), equalTo("NoVersificationMapping"));
//		assertThat(underTest.getVersificationMapping(VULG_VERSIFICATION, CATHOLIC_VERSIFICATION).toString(), equalTo("NoVersificationMapping"));
//	}
}
