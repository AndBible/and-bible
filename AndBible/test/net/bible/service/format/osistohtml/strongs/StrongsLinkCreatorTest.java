package net.bible.service.format.osistohtml.strongs;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import junit.framework.TestCase;

public class StrongsLinkCreatorTest extends TestCase {

	StrongsLinkCreator strongsLinkCreator = new StrongsLinkCreator();

	public void testCorrectText() {
		assertThat( strongsLinkCreator.process("see HEBREW for 12345"), equalTo("<a href='hdef:12345' class=''>see HEBREW for 12345</a>"));
	}

	public void testInvalidText() {
		assertThat( strongsLinkCreator.process("123\n456  88 "), equalTo("123\n456  88 "));
	}
}
