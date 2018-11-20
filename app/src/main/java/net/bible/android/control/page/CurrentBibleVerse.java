/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.page;

import net.bible.android.control.versification.ConvertibleVerse;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CurrentBibleVerse {
	
	private ConvertibleVerse verseVersificationSelected = new ConvertibleVerse(Versifications.instance().getVersification(Versifications.DEFAULT_V11N), BibleBook.GEN, 1, 1);

	public int getCurrentBibleBookNo() {
		return verseVersificationSelected.getBook().ordinal();
	}

	public BibleBook getCurrentBibleBook() {
		return verseVersificationSelected.getBook();
	}
	
	public Verse getVerseSelected(Versification versification) {
		return verseVersificationSelected.getVerse(versification);
	}
	public void setVerseSelected(Versification versification, Verse verseSelected) {
		verseVersificationSelected.setVerse(versification, verseSelected);
	}
	public void setChapterVerse(ChapterVerse chapterVerse) {
		verseVersificationSelected.setChapterVerse(chapterVerse);
	}
	public ChapterVerse getChapterVerse() {
		return verseVersificationSelected.getChapterVerse();
	}
	public Versification getVersificationOfLastSelectedVerse() {
		return verseVersificationSelected.getVerse().getVersification();
	}

	public JSONObject getStateJson() throws JSONException {
		return verseVersificationSelected.getStateJson();
	}
	
	public void restoreState(JSONObject jsonObject) throws JSONException {
		verseVersificationSelected.restoreState(jsonObject);
	}
}
