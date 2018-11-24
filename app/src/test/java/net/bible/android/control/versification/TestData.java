package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemGerman;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.SystemKJVA;
import org.crosswire.jsword.versification.system.SystemLXX;
import org.crosswire.jsword.versification.system.SystemLeningrad;
import org.crosswire.jsword.versification.system.SystemLuther;
import org.crosswire.jsword.versification.system.SystemMT;
import org.crosswire.jsword.versification.system.SystemNRSV;
import org.crosswire.jsword.versification.system.SystemSegond;
import org.crosswire.jsword.versification.system.SystemSynodal;
import org.crosswire.jsword.versification.system.SystemSynodalProt;
import org.crosswire.jsword.versification.system.SystemVulg;
import org.crosswire.jsword.versification.system.Versifications;

public interface TestData {
	Versification KJV = Versifications.instance().getVersification(SystemKJV.V11N_NAME);
	Versification KJVA = Versifications.instance().getVersification(SystemKJVA.V11N_NAME);
	Versification SYNODAL_PROT = Versifications.instance().getVersification(SystemSynodalProt.V11N_NAME);
	Versification NRSV = Versifications.instance().getVersification(SystemNRSV.V11N_NAME);
	Versification LXX = Versifications.instance().getVersification(SystemLXX.V11N_NAME);
	Versification SEGOND = Versifications.instance().getVersification(SystemSegond.V11N_NAME);
	Versification MT = Versifications.instance().getVersification(SystemMT.V11N_NAME);
	Versification GERMAN = Versifications.instance().getVersification(SystemGerman.V11N_NAME);
	Versification LENINGRAD = Versifications.instance().getVersification(SystemLeningrad.V11N_NAME);
	Versification LUTHER = Versifications.instance().getVersification(SystemLuther.V11N_NAME);
	Versification SYNODAL = Versifications.instance().getVersification(SystemSynodal.V11N_NAME);
	Versification VULGATE = Versifications.instance().getVersification(SystemVulg.V11N_NAME);

	// these verses should be equivalent
	Verse KJV_PS_14_2 = new Verse(KJV, BibleBook.PS, 14, 2);
	Verse KJV_PS_14_4 = new Verse(KJV, BibleBook.PS, 14, 4);
	VerseRange KJV_PS_14_2_4 = new VerseRange(KJV, KJV_PS_14_2, KJV_PS_14_4);

	Verse SYN_PROT_PS_13_2 = new Verse(SYNODAL_PROT, BibleBook.PS, 13, 2);
	Verse SYN_PROT_PS_13_4 = new Verse(SYNODAL_PROT, BibleBook.PS, 13, 4);
	VerseRange SYN_PROT_PS_13_2_4 = new VerseRange(SYNODAL_PROT, SYN_PROT_PS_13_2, SYN_PROT_PS_13_4);

	Verse KJVA_1MACCPS_1_2 = new Verse(KJVA, BibleBook.MACC1, 1, 2);
	Verse KJVA_1MACCPS_1_3 = new Verse(KJVA, BibleBook.MACC1, 1, 3);
	VerseRange KJVA_1MACC_1_2_3 = new VerseRange(KJVA, KJVA_1MACCPS_1_2, KJVA_1MACCPS_1_3);
}
