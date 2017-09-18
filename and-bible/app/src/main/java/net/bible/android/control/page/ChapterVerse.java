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

	public static ChapterVerse fromHtmlId(String chapterDotVerse) {
		String[] strings = chapterDotVerse.split("\\.");
		int chapter = Integer.parseInt(strings[0]);
		int verse = Integer.parseInt(strings[1]);
		return new ChapterVerse(chapter, verse);
	}

	public static ChapterVerse fromVerse(Verse pVerse) {
		int chapter = pVerse.getChapter();
		int verse = pVerse.getVerse();
		return new ChapterVerse(chapter, verse);
	}

	/**
	 * The format used for ids in html
	 */
	public String toHtmlId() {
		return chapter + "." +verse;
	}

	public boolean after(ChapterVerse other) {
		return chapter>other.chapter ||
				(chapter==other.chapter && verse>other.verse);
	}

	public boolean before(ChapterVerse other) {
		return chapter<other.chapter ||
				(chapter==other.chapter && verse<other.verse);
	}

	public boolean sameChapter(ChapterVerse other) {
		return chapter==other.chapter;
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
