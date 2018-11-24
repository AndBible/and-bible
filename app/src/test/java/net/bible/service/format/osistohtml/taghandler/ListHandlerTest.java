package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ListHandlerTest {
	private HtmlTextWriter writer;
	private ListHandler listHandler;
	private ListItemHandler itemHandler;

	@Before
	public void setUp() throws Exception {
		writer = new HtmlTextWriter();

		listHandler = new ListHandler(writer);
		itemHandler = new ListItemHandler(writer);
	}

	@Test
	public void testListItems() {
		AttributesImpl attrs = new AttributesImpl();
		listHandler.start(attrs);
		
		itemHandler.start(attrs);
		writer.write("item 1");
		itemHandler.end();

		itemHandler.start(attrs);
		writer.write("item 2");
		itemHandler.end();

		listHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<ul><li>item 1</li><li>item 2</li></ul>"));
	}

}
