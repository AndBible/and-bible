package net.bible.service.format.osistohtml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TextWriterTest {

	private TextWriter textWriter;
	@Before
	public void setUp() throws Exception {
		textWriter = new TextWriter();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testHierarchicalBeginInsertAt() throws Exception {
		textWriter.write("ab");
		int afterAb = textWriter.getPosition();
		textWriter.write("kl");

		textWriter.beginInsertAt(afterAb);
		textWriter.write("cd");
		
		int afterCd = textWriter.getPosition();
		textWriter.write("ef");
		
		// should be ignored because already inserting
		textWriter.beginInsertAt(afterCd);
		textWriter.write("gh");

		textWriter.finishInserting();
		
		textWriter.write("ij");
		textWriter.finishInserting();

		textWriter.write("mn");

		assertThat(textWriter.getHtml(), equalTo("abcdefghijklmn"));
	}

}
