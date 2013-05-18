package net.bible.service.format.osistohtml.strongs;

import junit.framework.TestCase;

public class StrongsLinkCreatorTest extends TestCase {

	StrongsLinkCreator strongsLinkCreator = new StrongsLinkCreator();
	
	public void testProcess() {
		assertEquals( strongsLinkCreator.process("123\n456  88 "), "123\n456  88 ");
	}

}
