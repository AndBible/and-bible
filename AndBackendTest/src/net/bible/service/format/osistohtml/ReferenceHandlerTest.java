package net.bible.service.format.osistohtml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class ReferenceHandlerTest extends TestCase {

	public void testReferenceRegExp() {
		// sword://StrongsRealGreek/01909
		// StrongsHebrew:00411
		Pattern p = Pattern.compile("(sword://)?[A-za-z_]*[/:]{1}[A-Za-z0-9_]*");
		Matcher m1 = p.matcher("sword://StrongsRealGreek/01909");
		assertTrue(m1.matches());
		Matcher m2 = p.matcher("StrongsHebrew:00411");
		assertTrue(m2.matches());
		Matcher m3 = p.matcher("Matt 13:4");
		assertTrue(!m3.matches());
	}
}
