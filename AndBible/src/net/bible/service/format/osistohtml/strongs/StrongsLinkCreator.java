package net.bible.service.format.osistohtml.strongs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bible.service.format.osistohtml.preprocessor.TextPreprocessor;

/** Used with StrongsGreek and StrongsHebrew to find text like 'see HEBREW for 0433' and 'see GREEK for 1223' and converts to links
 * 
 * @author denha1m
 *
 */
public class StrongsLinkCreator implements TextPreprocessor {
	
	static Pattern patt = Pattern.compile( "see (HEBREW|GREEK) for (\\d{1,5})"); //".*see ([HEBREW|GREEK]) for (\\d{1,5}).*");

	private static final String LINK_CSS_CLASS = "strongsLarge";
	
	public String process(String text) {
		String result="";
		Matcher m = patt.matcher(text);
		
		int unmatchedStartPos = 0;
		
		while (m.find()) {
			int start = m.start();
			int end = m.end();
			String lang = m.group(1);
			String refNo = m.group(2);
			
			// select Hebrew or Greek protocol
			String protocol = StrongsUtil.getStrongsProtocol(lang);

			// append test between matches
			result += text.substring(unmatchedStartPos, start);
			
			// append the actual link to the Strongs ref
			result += StrongsUtil.createStrongsLink(protocol, refNo, m.group(), "");
			
			unmatchedStartPos = end;
		}
		
		// append any trailing space after the last match, or if no match then the whole string
		if (unmatchedStartPos<text.length()) {
			result += text.substring(unmatchedStartPos);
		}

		return result;
	}
	

}
