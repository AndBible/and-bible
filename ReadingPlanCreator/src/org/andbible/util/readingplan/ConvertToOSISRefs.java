package org.andbible.util.readingplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.crosswire.jsword.passage.PassageKeyFactory;

public class ConvertToOSISRefs {
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
			String[] refs = match.split(",");
			if (refs.length!=2) {
				System.out.println("ERROR not 2 parts:"+refs);
			}
			System.out.println(match+" split into "+refs.length+" parts");
			

			String repl = "";
			boolean isFirst = true;
			for (String ref : refs) {
				if (!isFirst) {
					repl += ",";
				}
				try {
					repl += PassageKeyFactory.instance().getKey(ref).getOsisRef();
        		} catch (Exception e) {
		        	System.out.println("ERROR:ref="+ref);
        		}
				isFirst = false;
			}
			m.appendReplacement(retVal, repl);
			
		}
		System.out.println("Total rows: "+count);
		
		// append any trailing space after the last match, or if no match then the whole string
		m.appendTail(retVal);

		return retVal.toString();
	}
}
