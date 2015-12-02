package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/**
 * A cell within a row within a table.
 * See TableHandler for full description and example.
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class TableCellHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public TableCellHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_CELL;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<td>");
	}

	@Override
	public void end() {
		writer.write("</td>");
	}
}
