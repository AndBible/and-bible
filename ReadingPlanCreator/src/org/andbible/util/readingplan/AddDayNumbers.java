package org.andbible.util.readingplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddDayNumbers {
	/** E.g.
     __________________________________________________________________

   I. It is no sign one way or the other, that religious affections are

	 */
	static Pattern pattern = Pattern.compile( "^(.+)", Pattern.MULTILINE);

	public String filter(String in) {
		Matcher m = pattern.matcher(in);
		StringBuffer retVal = new StringBuffer();
		
		int count = 0;
		while (m.find()) {
			String match = m.group(1);
			count++;
			m.appendReplacement(retVal, count+"="+match);
		}
		System.out.println("Total Days: "+count);
		
		// append any trailing space after the last match, or if no match then the whole string
		m.appendTail(retVal);

		return retVal.toString();
	}
}
