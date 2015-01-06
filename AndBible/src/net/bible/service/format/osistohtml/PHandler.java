package net.bible.service.format.osistohtml;

import net.bible.service.common.Logger;

import org.xml.sax.Attributes;

/** Paragraph <p>...</p>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class PHandler {

	private HtmlTextWriter writer;
	
	@SuppressWarnings("unused")
	private OsisToHtmlParameters parameters;
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("LHandler");

	public PHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	public String getTagName() {
        return "p";
    }

	public void start(Attributes attrs) {
		writer.write("<p>");
	}

	public void end() {
		writer.write("</p>");
	}
}
