package net.bible.service.format.osistohtml;

import net.bible.service.common.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** Handle <figure src="imagefile.jpg" /> to display pictures
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class FigureHandler {

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	private static final Logger log = new Logger("LHandler");

	public FigureHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	public String getTagName() {
        return "figure";
    }

	public void start(Attributes attrs) {
		// Refer to Gen 3:14 in ESV for example use of type=x-indent
		String src = attrs.getValue(OSISUtil.ATTRIBUTE_FIGURE_SRC);
		
		if (StringUtils.isNotEmpty(src)) {
			writer.write("<img src='"+parameters.getModuleBasePath()+"/"+src+"'/>");
		}
	}

	public void end() {
	}
}
