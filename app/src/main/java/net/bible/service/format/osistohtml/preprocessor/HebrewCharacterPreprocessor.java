/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.format.osistohtml.preprocessor;

import net.bible.service.common.CommonUtils;
import android.os.Build;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class HebrewCharacterPreprocessor implements TextPreprocessor {

	// the following characters are not handled well in Android 2.2 & 2.3 and
	// need special processing which for all except Sof Pasuq means removal
	// puctuation char at the end of hebrew verses that looks like a ':'
	private static final String HEBREW_SOF_PASUQ_CHAR = "\u05C3";
	// vowels are on the first row and cantillations on the second
	private static final char[] HEBREW_VOWELS_AND_CANTILLATIONS = new char[] {
			'\u05B0', '\u05B1', '\u05B2', '\u05B3', '\u05B4', '\u05B5',
			'\u05B6', '\u05B7', '\u05B8', '\u05B9', '\u05BA', '\u05BB',
			'\u05BC', '\u05BD', '\u05BE', '\u05BF', '\u05C1', '\u05C2',
			'\u0591', '\u0592', '\u0593', '\u0594', '\u0595', '\u0596',
			'\u0597', '\u0598', '\u0599', '\u059A', '\u059B', '\u059C',
			'\u059D', '\u059E', '\u05A0', '\u05A1', '\u05A2', '\u05A3',
			'\u05A4', '\u05A5', '\u05A6', '\u05A7', '\u05A8', '\u05A9',
			'\u05AA', '\u05AB', '\u05AC', '\u05AD', '\u05AE', '\u05AF' };


	/**
	 * Some characters are not handled well in Android 2.2 & 2.3 and need
	 * special processing which for all except Sof Pasuq means removal
	 * 
	 * @param text
	 * @return adjusted string
	 */
	@Override
	public String process(String text) {
		if (isVowelsBugFixed()) {
			return text;
		} else {
			return doHebrewCharacterAdjustments(text);
		}
	}
	
	/** vowels rtl problem fixed in recent cyanogenmod and 4.0.3 */
	private boolean isVowelsBugFixed() {
		return Build.VERSION.SDK_INT >= 15 || //Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
			   (Build.VERSION.SDK_INT >= 10 &&System.getProperty("os.version").contains("cyanogenmod")); // 10 is GINGERBREAD_MR1 (2.3.3) 	
	}

	private String doHebrewCharacterAdjustments(String s) {
		// remove Hebrew vowels because i) they confuse bidi and ii) they are
		// not positioned correctly under/over the appropriate letter
		// http://groups.google.com/group/android-contrib/browse_thread/thread/5b6b079f9ec7792a?pli=1
		s = CommonUtils.INSTANCE.remove(s, HEBREW_VOWELS_AND_CANTILLATIONS);

		// even without vowel points the : at the end of each verse confuses
		// Android's bidi but specifying the char as rtl helps
		s = s.replace(HEBREW_SOF_PASUQ_CHAR, "<span dir='rtl'>"
				+ HEBREW_SOF_PASUQ_CHAR + "</span> ");
		return s;
	}

}
