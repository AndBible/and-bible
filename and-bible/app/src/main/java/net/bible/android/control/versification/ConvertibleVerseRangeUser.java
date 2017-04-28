package net.bible.android.control.versification;

/**
 * Something that uses/has a {@link ConvertibleVerseRange}
 * Allows BookmarkDto and MyNoteDto to use comparator logic.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public interface ConvertibleVerseRangeUser {

	ConvertibleVerseRange getConvertibleVerseRange();
}
