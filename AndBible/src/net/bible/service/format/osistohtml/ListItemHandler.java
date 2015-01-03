package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/**
 * The main content of a list is encoded using the item element.
 * See ListHandler for full description
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class ListItemHandler {

	private HtmlTextWriter writer;
	
	public ListItemHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	public String getTagName() {
        return "item";
    }

	public void start(Attributes attrs) {
		writer.write("<li>");
	}

	public void end() {
		writer.write("</li>");
	}
}
