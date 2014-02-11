package net.bible.service.format.osistohtml;

import net.bible.service.common.Logger;

import org.apache.commons.lang.StringUtils;
import org.xml.sax.Attributes;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TagHandlerHelper {

	private static final Logger log = new Logger("TagHandlerHelper");

	/** support defaultvalue with attribute fetch
	 */
	public static String getAttribute(String attributeName, Attributes attrs, String defaultValue) {
		String attrValue = attrs.getValue(attributeName);
		if (attrValue != null) {
			return attrValue;
		} else {
			return defaultValue;
		}
	}

	/** support defaultvalue with attribute fetch
	 */
	public static int getAttribute(String attributeName, Attributes attrs, int defaultValue) {
		int retval = defaultValue;
		try {
			String attrValue = attrs.getValue(attributeName);
			if (attrValue != null) {
				retval = Integer.parseInt(attrValue);
			}
		} catch (Exception e) {
			log.warn("Non numeric but expected integer for "+attributeName);
		}
		return retval;
	}

	/**
	 * see if an attribute exists and has a value
	 * 
	 * @param attributeName
	 * @param attrs
	 * @return
	 */
	public static boolean isAttr(String attributeName, Attributes attrs) {
		String attrValue = attrs.getValue(attributeName);
		return StringUtils.isNotEmpty(attrValue);
	}

    /** return verse from osis id of format book.chap.verse
     * 
     * @param ososID osis Id
     * @return verse number
     */
    public static int osisIdToVerseNum(String osisID) {
       /* You have to use "\\.", the first backslash is interpreted as an escape by the
        Java compiler, so you have to use two to get a String that contains one
        backslash and a dot, which is what you want the regexp engine to see.*/
    	if (osisID!=null) {
	        String[] parts = osisID.split("\\.");
	        if (parts.length>1) {
	            String verse =  parts[parts.length-1];
	            return Integer.valueOf(verse);
	        }
    	}
        return 0;
    }
    
    public static void printAttributes(Attributes attrs) {
		for (int i=0; i<attrs.getLength(); i++) {
			log.debug(attrs.getLocalName(i)+":"+attrs.getValue(i));
		}
    }
}
