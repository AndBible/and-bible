package net.bible.service.format.osistohtml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class HtmlTextWriterTest {

	private HtmlTextWriter htmlTextWriter;
	@Before
	public void setUp() throws Exception {
		htmlTextWriter = new HtmlTextWriter();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testHierarchicalBeginInsertAt() throws Exception {
		htmlTextWriter.write("ab");
		int afterAb = htmlTextWriter.getPosition();
		htmlTextWriter.write("kl");

		htmlTextWriter.beginInsertAt(afterAb);
		htmlTextWriter.write("cd");
		
		int afterCd = htmlTextWriter.getPosition();
		htmlTextWriter.write("ef");
		
		// should be ignored because already inserting
		htmlTextWriter.beginInsertAt(afterCd);
		htmlTextWriter.write("gh");

		htmlTextWriter.finishInserting();
		
		htmlTextWriter.write("ij");
		htmlTextWriter.finishInserting();

		htmlTextWriter.write("mn");

		assertThat(htmlTextWriter.getHtml(), equalTo("abcdefghijklmn"));
	}

}
