package net.bible.service.format.osistohtml.strongs;

import net.bible.service.format.osistohtml.strongs.StrongsLinkCreator;
import junit.framework.TestCase;

public class StrongsLinkCreatorTest extends TestCase {

	private static final String testInput = "1252  diakrino  dee-ak-ree'-no\n\n  from 1223 and 2919; to separate thoroughly, i.e. (literally and\n	 reflexively) to withdraw from, or (by implication) oppose;\n"+
	" see GREEK for 1223\n"+
	" see GREEK for 2919\n"+
	" see HEBREW for 0772";


	public void testLink() {
		StrongsLinkCreator testObj = new StrongsLinkCreator();
		String out = testObj.process(testInput);
		assertTrue(out.contains("<a href="));
	}
	public void testNoLink() {
		StrongsLinkCreator testObj = new StrongsLinkCreator();
		String testIn = "abc\n123";
		String out = testObj.process(testIn);
		assertEquals(testIn, out);
	}
}
