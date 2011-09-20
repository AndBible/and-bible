package net.bible.service.format.osistohtml.strongs;

import net.bible.service.common.Constants;

import org.apache.commons.lang.StringUtils;

public class StrongsUtil {

	private static final String DEFAULT_CSS_CLASS = "strongs";
	
	/**
	 * create an html link for teh passed in strongs number and protocol
	 * @param protocol = G or H
	 * @param strongsNumber
	 * @return
	 */
	public static String createStrongsLink(String protocol, String strongsNumber) {
		return createStrongsLink(protocol, strongsNumber, strongsNumber, DEFAULT_CSS_CLASS);
	}
	
	public static String createStrongsLink(String protocol, String strongsNumber, String content, String cssClass) {
		// pad with leading zeros to 5 characters
		String paddedRef = StringUtils.leftPad(strongsNumber, 5, "0");

		StringBuilder tag = new StringBuilder();
		// create opening tag for Strong's link
		tag.append("<a href='");

		// calculate uri e.g. H:01234
		tag.append(protocol).append(":").append(paddedRef);

		// set css class
		tag.append("' class='"+cssClass+"'>");

		// descriptive string
		tag.append(content);

		// link closing tag
		tag.append("</a>");
		
		String strTag = tag.toString();
		return strTag;
	}

	public static String getStrongsProtocol(String ref) {
		if (ref.startsWith("H")) {
			return Constants.HEBREW_DEF_PROTOCOL;
		} else if (ref.startsWith("G")) {
			return Constants.GREEK_DEF_PROTOCOL;
		}
		return null;
	}
}
