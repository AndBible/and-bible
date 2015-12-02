package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OSISUtil2;

import org.xml.sax.Attributes;

/**
 * The <divineName> tag is reserved for representations of the tetragrammaton יהוה (YHWH). 
 * These occur in the Old Testament as Lord, God and Yah. Not every instance of Lord or God is a translation of this.
 * E.g. <divineName type="yhwh">LORD</divineName>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class DivineNameHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public DivineNameHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil2.OSIS_ELEMENT_DIVINENAME;
    }

	@Override
	public void start(Attributes attrs) {
		// start span with CSS class
		writer.write("<span class='divineName'>");	}

	@Override
	public void end() {
		writer.write("</span>");
	}
}
