package net.bible.service.format.osistohtml.strongs;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class StrongsLinkCreatorTest {

	StrongsLinkCreator strongsLinkCreator = new StrongsLinkCreator();

	@Test
	public void testCorrectText() {
		assertThat( strongsLinkCreator.process("see HEBREW for 12345"), equalTo("<a href='hdef:12345' class=''>see HEBREW for 12345</a>"));
	}

	@Test
	public void testInvalidText() {
		assertThat( strongsLinkCreator.process("123\n456  88 "), equalTo("123\n456  88 "));
	}
}
