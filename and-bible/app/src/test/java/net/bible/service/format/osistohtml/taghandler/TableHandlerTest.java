package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TableHandlerTest {
	private HtmlTextWriter writer;
	private TableHandler tableHandler;
	private TableRowHandler rowHandler;
	private TableCellHandler cellHandler;

	@Before
	public void setUp() throws Exception {
		writer = new HtmlTextWriter();

		tableHandler = new TableHandler(writer);
		rowHandler = new TableRowHandler(writer);
		cellHandler = new TableCellHandler(writer);
	}

	@Test
	public void testTableTrCell() {
		AttributesImpl attrs = new AttributesImpl();
		tableHandler.start(attrs);
		
		// row 1
		rowHandler.start(attrs);

		cellHandler.start(attrs);
		writer.write("row 1 cell 1");
		cellHandler.end();

		cellHandler.start(attrs);
		writer.write("row 1 cell 2");
		cellHandler.end();

		rowHandler.end();

		// row 2
		rowHandler.start(attrs);

		cellHandler.start(attrs);
		writer.write("row 2 cell 1");
		cellHandler.end();

		cellHandler.start(attrs);
		writer.write("row 2 cell 2");
		cellHandler.end();

		rowHandler.end();

		tableHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<table><tr><td>row 1 cell 1</td><td>row 1 cell 2</td></tr><tr><td>row 2 cell 1</td><td>row 2 cell 2</td></tr></table>"));
	}

}
