package net.bible.android.control.page;

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

	public ChapterVerse(int chapter, int verse) {
		this.chapter = chapter;
		this.verse = verse;
	}

	public ChapterVerse(String chapterDotVerse) {
		String[] strings = chapterDotVerse.split("\\.");
		chapter = Integer.parseInt(strings[0]);
		verse = Integer.parseInt(strings[1]);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ChapterVerse that = (ChapterVerse) o;

		if (chapter != that.chapter) return false;
		return verse == that.verse;
	}

	public int getChapter() {
		return chapter;
	}

	public int getVerse() {
		return verse;
	}
}
