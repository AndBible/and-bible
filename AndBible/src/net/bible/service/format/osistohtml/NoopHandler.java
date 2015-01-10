package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/** The lg or "line group" element is used to contain any group of poetic lines.  Poetic lines are handled at the line level by And Bible, not line group 
 * so this class does nothing.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class NoopHandler implements OsisTagHandler {

	public NoopHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
	}

	@Override
	public String getTagName() {
        return "";
    }

	@Override
	public void start(Attributes attrs) {
	}

	@Override
	public void end() {
	}
}
