package org.andbible.util.readingplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoveEmptyLines {
	/** E.g.
     __________________________________________________________________

   I. It is no sign one way or the other, that religious affections are

	 */
	static Pattern pattern = Pattern.compile( "(^\\s*\n)", Pattern.MULTILINE);

	public String filter(String in) {
		Matcher m = pattern.matcher(in);
		StringBuffer retVal = new StringBuffer();
		
		int count = 0;
		while (m.find()) {
			String prefix = m.group(1);
			count++;
			m.appendReplacement(retVal,  "");
		}
		System.out.println("Empty lines removed: "+count);
		
		// append any trailing space after the last match, or if no match then the whole string
		m.appendTail(retVal);

		return retVal.toString();
	}
}
