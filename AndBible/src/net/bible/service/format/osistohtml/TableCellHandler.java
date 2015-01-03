package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/**
 * A cell within a row within a table.
 * See TableHandler for full description and example.
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class TableCellHandler {

	private HtmlTextWriter writer;
	
	public TableCellHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	public String getTagName() {
        return "cell";
    }

	public void start(Attributes attrs) {
		writer.write("<td>");
	}

	public void end() {
		writer.write("</td>");
	}
}
