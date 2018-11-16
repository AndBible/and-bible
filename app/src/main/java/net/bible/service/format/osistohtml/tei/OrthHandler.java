package net.bible.service.format.osistohtml.tei;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.taghandler.HiHandler;

import org.xml.sax.Attributes;


/** Handle orth tag very similarly to hi tag
 * <orth>?????????</orth>
 * <orth rend="bold" type="trans">aneuthetos</orth>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class OrthHandler extends HiHandler {

    private final static String DEFAULT = "bold";

    public OrthHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
        super(parameters, writer);
    }
    
    @Override
    public String getTagName() {
        return TEIUtil.TEI_ELEMENT_ORTH;
    }

    @Override
    public void start(Attributes attrs) {
        String rend = attrs.getValue(TEIUtil.TEI_ATTR_REND);
        start(rend, DEFAULT);
    }
}
