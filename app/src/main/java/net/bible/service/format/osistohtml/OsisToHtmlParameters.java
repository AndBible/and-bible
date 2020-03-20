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

package net.bible.service.format.osistohtml;

import net.bible.android.control.bookmark.BookmarkStyle;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**Parameters passed into the Osis to HTML converter
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class OsisToHtmlParameters {
	private boolean showChapterDivider;
	private Integer chapter = null;
	private boolean asFragment = false;
	private String languageCode = "en";
	private boolean isLeftToRight = true;
	private boolean isShowTitles = true;
	private boolean isShowVerseNumbers = false;
	private boolean isVersePerline = false;
	private boolean isShowMyNotes = false;
	private boolean isShowBookmarks = false;
	private BookmarkStyle defaultBookmarkStyle = BookmarkStyle.YELLOW_STAR;
	private boolean isShowNotes = false;
	private boolean isAutoWrapUnwrappedRefsInNote = false;
	// used as a basis if a reference has only chapter and no book
    private Verse basisRef;
	private Versification documentVersification;
	private String font;

    private String cssClassForCustomFont;
	private boolean isShowStrongs = false;
	private boolean isShowMorphology = false;
	private boolean isRedLetter = false;
	private List<String> cssStylesheetList;
	private String extraFooter;
	private boolean convertStrongsRefsToLinks;
	private List<Verse> versesWithNotes;
	private Map<Integer, Set<BookmarkStyle>> bookmarkStylesByBookmarkedVerse;
	private URI moduleBasePath;
	private int indentDepth = 2;

	public String getCssStylesheets() {
		StringBuilder builder = new StringBuilder();
		if (cssStylesheetList!=null) {
			for (String styleSheet : cssStylesheetList) {
				builder.append(styleSheet);
			}
		}
		return builder.toString();
	}

	public Integer getChapter() {
		return chapter;
	}
	public void setChapter(Integer chapter) {
		this.chapter = chapter;
	}
	public void setShowChapterDivider(boolean showChapterDivider) {
		this.showChapterDivider = showChapterDivider;
	}
	public boolean isShowChapterDivider() {
		return showChapterDivider;
	}
	public boolean isAsFragment() {
		return asFragment;
	}
	public void setAsFragment(boolean asFragment) {
		this.asFragment = asFragment;
	}
	public String getLanguageCode() {
		return languageCode;
	}
	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}
	public boolean isLeftToRight() {
		return isLeftToRight;
	}
	public void setLeftToRight(boolean isLeftToRight) {
		this.isLeftToRight = isLeftToRight;
	}
	public boolean isShowTitles() {
		return isShowTitles;
	}
	public void setShowTitles(boolean isShowTitles) {
		this.isShowTitles = isShowTitles;
	}
	public boolean isShowVerseNumbers() {
		return isShowVerseNumbers;
	}
	public void setShowVerseNumbers(boolean isShowVerseNumbers) {
		this.isShowVerseNumbers = isShowVerseNumbers;
	}
	public boolean isVersePerline() {
		return isVersePerline;
	}
	public void setVersePerline(boolean isVersePerline) {
		this.isVersePerline = isVersePerline;
	}
	public boolean isShowMyNotes() {
		return isShowMyNotes;
	}
	public void setShowMyNotes(boolean isShowMyNotes) {
		this.isShowMyNotes = isShowMyNotes;
	}
	public boolean isShowBookmarks() {
		return isShowBookmarks;
	}
	public void setShowBookmarks(boolean isShowBookmarks) {
		this.isShowBookmarks = isShowBookmarks;
	}

	public void setDefaultBookmarkStyle(BookmarkStyle defaultBookmarkStyle) {
		this.defaultBookmarkStyle = defaultBookmarkStyle;
	}

	public boolean isShowNotes() {
		return isShowNotes;
	}
	public void setShowNotes(boolean isShowNotes) {
		this.isShowNotes = isShowNotes;
	}
	public boolean isAutoWrapUnwrappedRefsInNote() {
		return isAutoWrapUnwrappedRefsInNote;
	}
	public void setAutoWrapUnwrappedRefsInNote(boolean isAutoWrapUnwrappedRefsInNote) {
		this.isAutoWrapUnwrappedRefsInNote = isAutoWrapUnwrappedRefsInNote;
	}
	public boolean isShowStrongs() {
		return isShowStrongs;
	}
	public void setShowStrongs(boolean isShowStrongs) {
		this.isShowStrongs = isShowStrongs;
	}
	public boolean isShowMorphology() {
		return isShowMorphology;
	}
	public void setShowMorphology(boolean isShowMorphology) {
		this.isShowMorphology = isShowMorphology;
	}
	public List<String> getCssStylesheetList() {
		return cssStylesheetList;
	}
	public void setCssStylesheetList(List<String> cssStylesheetList) {
		this.cssStylesheetList = cssStylesheetList;
	}
	public String getExtraFooter() {
		return extraFooter;
	}
	public void setExtraFooter(String extraFooter) {
		this.extraFooter = extraFooter;
	}
	public Verse getBasisRef() {
		return basisRef;
	}
	public void setBasisRef(Key basisRef) {
		// KeyUtil always returns a Verse even if it is only Gen 1:1
		this.basisRef = KeyUtil.getVerse(basisRef);
	}
	public boolean isRedLetter() {
		return isRedLetter;
	}
	public void setRedLetter(boolean isRedLetter) {
		this.isRedLetter = isRedLetter;
	}
	public String getFont() {
		return font;
	}
	public void setFont(String font) {
		this.font = font;
	}
	public String getCssClassForCustomFont() {
		return cssClassForCustomFont;
	}
	public void setCssClassForCustomFont(String cssClassForCustomFont) {
		this.cssClassForCustomFont = cssClassForCustomFont;
	}
	public boolean isConvertStrongsRefsToLinks() {
		return convertStrongsRefsToLinks;
	}
	public void setConvertStrongsRefsToLinks(boolean convertStrongsRefsToLinks) {
		this.convertStrongsRefsToLinks = convertStrongsRefsToLinks;
	}
	public List<Verse> getVersesWithNotes() {
		return versesWithNotes;
	}
	public void setVersesWithNotes(List<Verse> versesWithNotes) {
		this.versesWithNotes = versesWithNotes;
	}
	public Map<Integer, Set<BookmarkStyle>> getBookmarkStylesByBookmarkedVerse() {
		return bookmarkStylesByBookmarkedVerse;
	}
	public void setBookmarkStylesByBookmarkedVerse(Map<Integer, Set<BookmarkStyle>> bookmarkStylesByBookmarkedVerse) {
		this.bookmarkStylesByBookmarkedVerse = bookmarkStylesByBookmarkedVerse;
	}
	public URI getModuleBasePath() {
		return moduleBasePath;
	}
	public void setModuleBasePath(URI moduleBasePath) {
		this.moduleBasePath = moduleBasePath;
	}
	public Versification getDocumentVersification() {
		if (documentVersification!=null) {
			return documentVersification;
		} else {
			return Versifications.instance().getVersification(Versifications.DEFAULT_V11N);
		}
	}
	public void setDocumentVersification(Versification documentVersification) {
		this.documentVersification = documentVersification;
	}
	
	public void setIndentDepth(int indentDepth) {
		this.indentDepth = indentDepth;
	}
	public int getIndentDepth() {
		return indentDepth; 
	}
}
