package net.bible.service.format.osistohtml.strongs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bible.service.format.osistohtml.preprocessor.TextPreprocessor;

/** Used with StrongsGreek and StrongsHebrew to find text like 'see HEBREW for 0433' and 'see GREEK for 1223' and converts to links
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class StrongsLinkCreator implements TextPreprocessor {
	
	static Pattern patt = Pattern.compile( "see (HEBREW|GREEK) for (\\d{1,5})"); //".*see ([HEBREW|GREEK]) for (\\d{1,5}).*");

	public String process(String text) {
		StringBuffer result=new StringBuffer();
		Matcher m = patt.matcher(text);
		
		while (m.find()) {
			String lang = m.group(1);
			String refNo = m.group(2);
			
			// select Hebrew or Greek protocol
			String protocol = StrongsUtil.getStrongsProtocol(lang);

			// append the actual link to the Strongs ref
			String refLink = StrongsUtil.createStrongsLink(protocol, refNo, m.group(), "");
			m.appendReplacement(result, refLink);
		}
		
		// append any trailing space after the last match, or if no match then the whole string
		m.appendTail(result);

		return result.toString();
	}
	

}
