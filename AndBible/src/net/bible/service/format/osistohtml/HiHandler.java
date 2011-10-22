package net.bible.service.format.osistohtml;

import static org.crosswire.jsword.book.OSISUtil.HI_ACROSTIC;
import static org.crosswire.jsword.book.OSISUtil.HI_BOLD;
import static org.crosswire.jsword.book.OSISUtil.HI_EMPHASIS;
import static org.crosswire.jsword.book.OSISUtil.HI_ILLUMINATED;
import static org.crosswire.jsword.book.OSISUtil.HI_ITALIC;
import static org.crosswire.jsword.book.OSISUtil.HI_LINETHROUGH;
import static org.crosswire.jsword.book.OSISUtil.HI_NORMAL;
import static org.crosswire.jsword.book.OSISUtil.HI_SMALL_CAPS;
import static org.crosswire.jsword.book.OSISUtil.HI_SUB;
import static org.crosswire.jsword.book.OSISUtil.HI_SUPER;
import static org.crosswire.jsword.book.OSISUtil.HI_UNDERLINE;

import java.util.Arrays;
import java.util.List;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;


/** Handle hi element e.g. <hi type="italic">the child with his mother Mary</hi>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class HiHandler {

	// possible values of type attribute
	private static final List<String> HI_TYPE_LIST = Arrays.asList(new String[]{HI_ACROSTIC, HI_BOLD, HI_EMPHASIS, HI_ILLUMINATED, HI_ITALIC, HI_LINETHROUGH, HI_NORMAL, HI_SMALL_CAPS, HI_SUB, HI_SUPER, HI_UNDERLINE});
	
	private final static String DEFAULT = "bold";

	private HtmlTextWriter writer;
	
	public HiHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "hi";
    }

	public void start(Attributes attrs) {
		String type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE);
		if (type==null || !HI_TYPE_LIST.contains(type)) {
			type = DEFAULT;
		}

		// start span with CSS class of 'hi_*' e.g. hi_bold
		writer.write("<span class=\'hi_"+type+"\'>");
	}

	public void end() {
		writer.write("</span>");
	}
}
