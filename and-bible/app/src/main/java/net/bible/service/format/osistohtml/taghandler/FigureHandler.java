package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** Handle <figure src="imagefile.jpg" /> to display pictures
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class FigureHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("LHandler");

	public FigureHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_FIGURE;
    }

	@Override
	public void start(Attributes attrs) {
		// Refer to Gen 3:14 in ESV for example use of type=x-indent
		String src = attrs.getValue(OSISUtil.ATTRIBUTE_FIGURE_SRC);
		
		if (StringUtils.isNotEmpty(src)) {
			writer.write("<img class='sword' src='"+parameters.getModuleBasePath()+"/"+src+"'/>");
		}
	}

	@Override
	public void end() {
	}
}
