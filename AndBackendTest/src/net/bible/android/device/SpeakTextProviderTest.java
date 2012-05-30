package net.bible.android.device;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class SpeakTextProviderTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}


	public void testRegExp() {
		String text = "The quick brown Foxy Lady, named Stan, jumped over the slow granny cooking potatoes.";
		List<String> chunks = breakUpText(text);
		// add on the final part of the text
		for (String t : chunks) {
			System.out.println(t.length()+":"+t);
		}
	}
	
	private static Pattern  breakPattern = Pattern.compile(".{10,20}[a-z][,.?!][\\s]{1,}");
	private List<String> breakUpText(String text) {
		List<String> chunks = new ArrayList<String>();
		Matcher matcher = breakPattern.matcher(text);

		int matchedUpTo = 0;
		while (matcher.find()) {
			// -1 because the pattern includes a char after the last space
			int nextEnd = matcher.end();
			chunks.add(text.substring(matchedUpTo, nextEnd));
			matchedUpTo = nextEnd;
		}
		// add on the final part of the text
		chunks.add(text.substring(matchedUpTo));

		return chunks;
	}

}
