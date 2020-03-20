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

package net.bible.android.control.versification;

import java.util.ArrayList;
import java.util.List;

import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

/** 
 * Enable separation of Scripture books 
 * Not complete because dc fragments are sometimes embedded within books like Esther and Daniel 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Scripture {

	private static final Versification SCRIPTURAL_V11N = Versifications.instance().getVersification("KJV");
	
	private final static List<BibleBook> INTROS = new ArrayList<BibleBook>();
	static {
		INTROS.add(BibleBook.INTRO_BIBLE);
		INTROS.add(BibleBook.INTRO_OT);
		INTROS.add(BibleBook.INTRO_NT);
	}
	
	/** TODO: needs to be improved because some books contain extra chapters which are non-scriptural
	 */
	public static boolean isScripture(BibleBook bibleBook) {
		return SCRIPTURAL_V11N.containsBook(bibleBook) && !INTROS.contains(bibleBook);
	}

	public static boolean isIntro(BibleBook bibleBook) {
		return INTROS.contains(bibleBook);
	}
}
