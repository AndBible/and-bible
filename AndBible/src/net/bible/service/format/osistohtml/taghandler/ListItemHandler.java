package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/**
 * The main content of a list is encoded using the item element.
 * See ListHandler for full description
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class ListItemHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public ListItemHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_ITEM;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<li>");
	}

	@Override
	public void end() {
		writer.write("</li>");
	}
}
