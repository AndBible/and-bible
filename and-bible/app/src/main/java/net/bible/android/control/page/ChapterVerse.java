package net.bible.android.control.page;

import net.bible.android.SharedConstants;

import org.crosswire.jsword.passage.Verse;

/**
 * Represent a chapter and verse
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class ChapterVerse {
	private final int chapter;
	private final int verse;

	public static final ChapterVerse NO_VALUE = new ChapterVerse(SharedConstants.NO_VALUE, SharedConstants.NO_VALUE);

	public ChapterVerse(int chapter, int verse) {
		this.chapter = chapter;
		this.verse = verse;
	}

	public ChapterVerse(String chapterDotVerse) {
		String[] strings = chapterDotVerse.split("\\.");
		chapter = Integer.parseInt(strings[0]);
		verse = Integer.parseInt(strings[1]);
	}

	public ChapterVerse(Verse pVerse) {
		chapter = pVerse.getChapter();
		verse = pVerse.getVerse();
	}

	/**
	 * The format used for ids in html
	 */
	public String toHtmlId() {
		return chapter + "." +verse;
	}

	@Override
	public String toString() {
		return "ChapterVerse{" +
				"chapter=" + chapter +
				", verse=" + verse +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ChapterVerse that = (ChapterVerse) o;
		return chapter==that.chapter && verse==that.verse;
	}

	public int getChapter() {
		return chapter;
	}

	public int getVerse() {
		return verse;
	}
}
