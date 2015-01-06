package net.bible.service.format.osistohtml;

import net.bible.service.common.Constants.HTML;
import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.PassageInfo;

import org.xml.sax.Attributes;

/** Line break
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class LbHandler {

	private PassageInfo passageInfo;
	
	private HtmlTextWriter writer;
	
	@SuppressWarnings("unused")
	private OsisToHtmlParameters parameters;
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("LHandler");

	public LbHandler(OsisToHtmlParameters parameters, PassageInfo passageInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.passageInfo = passageInfo;
		this.writer = writer;
	}
	
	public String getTagName() {
        return "lb";
    }

	public void start(Attributes attrs) {
		if (passageInfo.isAnyTextWritten) {
			writer.write(HTML.BR);
		}
	}

	public void endL() {
	}
}
