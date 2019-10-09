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

package net.bible.service.db.bookmark;

import net.bible.android.control.speak.PlaybackSettings;
import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.android.control.versification.sort.ConvertibleVerseRangeUser;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkDto implements ConvertibleVerseRangeUser {
	private Long id;
	private ConvertibleVerseRange convertibleVerseRange;
	private Date createdOn;
	private PlaybackSettings playbackSettings = null;

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public VerseRange getVerseRange() {
		return convertibleVerseRange.getVerseRange();
	}
	public VerseRange getVerseRange(Versification versification) {
		return convertibleVerseRange.getVerseRange(versification);
	}
	public void setVerseRange(VerseRange verseRange) {
		this.convertibleVerseRange = new ConvertibleVerseRange(verseRange);
	}
	public Date getCreatedOn() {
		return createdOn;
	}
	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	@Nullable
	public PlaybackSettings getPlaybackSettings(){
		return playbackSettings;
	}

	public void setPlaybackSettings(PlaybackSettings playbackSettings) {
		this.playbackSettings = playbackSettings;
	}

	public @Nullable Book getSpeakBook() {
	    if (playbackSettings != null && playbackSettings.getBookId() != null) {
			return Books.installed().getBook(playbackSettings.getBookId());
		}
		else {
			return null;
		}
	}

	@Override
	public ConvertibleVerseRange getConvertibleVerseRange() {
		return convertibleVerseRange;
	}

	@Override
	public String toString() {
		return "BookmarkDto{" +
				"convertibleVerseRange=" + convertibleVerseRange +
				'}';
	}

	/* (non-Javadoc)
			 * @see java.lang.Object#hashCode()
			 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		if (convertibleVerseRange ==null || convertibleVerseRange.getVerseRange()==null) {
			result = prime * result;
		} else {
			VerseRange verseRange = convertibleVerseRange.getVerseRange();
			result = prime * result + verseRange.hashCode();
		}
		return result;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BookmarkDto other = (BookmarkDto) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (convertibleVerseRange == null) {
			if (other.convertibleVerseRange != null)
				return false;
		} else if (!convertibleVerseRange.equals(other.convertibleVerseRange))
			return false;
		return true;
	}
}
