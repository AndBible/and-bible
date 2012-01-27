package org.andbible.util.readingplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoveLineStart {
	/** E.g.
     __________________________________________________________________

   I. It is no sign one way or the other, that religious affections are

	 */
	static Pattern prefixPattern = Pattern.compile( "^([A-Za-z0-9, ]+ --- )", Pattern.MULTILINE);

	public String filter(String in) {
		Matcher m = prefixPattern.matcher(in);
		StringBuffer retVal = new StringBuffer();
		
		while (m.find()) {
			String prefix = m.group(1);
			System.out.println("Prefix "+prefix);
			
			m.appendReplacement(retVal,  "");
		}
		
		// append any trailing space after the last match, or if no match then the whole string
		m.appendTail(retVal);

		return retVal.toString();
	}
}
