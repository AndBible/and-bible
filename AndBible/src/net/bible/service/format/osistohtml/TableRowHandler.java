package net.bible.service.format.osistohtml;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/**
 * A new row within a table.
 * See TableHandler for full description and example.
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class TableRowHandler implements OsisTagHandler{

	private HtmlTextWriter writer;
	
	public TableRowHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_ROW;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<tr>");
	}

	@Override
	public void end() {
		writer.write("</tr>");
	}
}
