package net.bible.android.view.activity.page;

import net.bible.android.control.page.ChapterVerse;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import java.util.HashSet;
import java.util.Set;

/**
 * Handle verse selection logic.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class ChapterVerseRange {

	private final Versification v11n;
	private final BibleBook bibleBook;
	private ChapterVerse start;
	private ChapterVerse end;

	private static final ChapterVerse NO_SELECTION = ChapterVerse.NO_VALUE;

	public ChapterVerseRange(Versification v11n, BibleBook bibleBook, ChapterVerse start, ChapterVerse end) {
		this.v11n = v11n;
		this.bibleBook = bibleBook;
		this.start = start;
		this.end = end;
	}

	public ChapterVerseRange clone() {
		return new ChapterVerseRange(v11n, bibleBook, getStart(), getEnd());
	}

	public void alter(ChapterVerse verse) {
		if (verse.after(end)) {
			end = verse;
		} else if (verse.before(start)) {
			start = verse;
		} else if (verse.after(start)) {
			// inc/dec are tricky when we don't know how many verses in chapters
			if (verse.getVerse()>1) {
				end = new ChapterVerse(verse.getChapter(), verse.getVerse()-1);
			} else {
				end = verse;
			}
		} else if (verse.equals(start) && start.equals(end)) {
			start = NO_SELECTION;
			end = NO_SELECTION;
		} else if (verse.equals(start)) {
			// Inc/dec are tricky when we don't know how many verses in chapters.
			// So there is a flaw in that the first verse cannot be deselected if selection spans multiple chapters
			if (start.sameChapter(end) && start.sameChapter(verse)) {
				start = new ChapterVerse(verse.getChapter(), verse.getVerse()+1);
			}
		}
	}

	public Set<ChapterVerse> getExtrasIn(ChapterVerseRange other) {
		VerseRange verseRange = createVerseRange();
		VerseRange otherVerseRange = other.createVerseRange();
		Verse[] otherVerses = otherVerseRange.toVerseArray();

		Set<ChapterVerse> extras = new HashSet<>();
		for (Verse otherVerse : otherVerses) {
			if (!verseRange.contains(otherVerse)) {
				extras.add(new ChapterVerse(otherVerse.getChapter(), otherVerse.getVerse()));
			}
		}
		return extras;
	}

	public boolean contains(ChapterVerse verse) {
		return verse.equals(start) || verse.equals(end) || verse.after(start) && verse.before(end);
	}

	private VerseRange createVerseRange() {
		return new VerseRange(
				v11n,
				new Verse(v11n, bibleBook, start.getChapter(), start.getVerse()),
				new Verse(v11n, bibleBook, end.getChapter(), end.getVerse())
		);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ChapterVerseRange that = (ChapterVerseRange) o;

		return start.equals(that.start) && end.equals(that.end);
	}

	@Override
	public int hashCode() {
		int result = start.hashCode();
		result = 31 * result + end.hashCode();
		return result;
	}

	public ChapterVerse getStart() {
		return start;
	}

	public ChapterVerse getEnd() {
		return end;
	}

	public boolean isEmpty() {
		return start.equals(ChapterVerse.NO_VALUE) || end.equals(ChapterVerse.NO_VALUE);
	}
}
