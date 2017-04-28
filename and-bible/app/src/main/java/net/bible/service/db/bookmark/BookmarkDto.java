package net.bible.service.db.bookmark;

import android.support.annotation.NonNull;

import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.android.control.versification.ConvertibleVerseRangeUser;

import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;

import java.util.Comparator;
import java.util.Date;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkDto implements ConvertibleVerseRangeUser {
	private Long id;
	private ConvertibleVerseRange convertibleVerseRange;
	private Date createdOn;

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

	/** Compare by Create date - most recent first */
	public static Comparator<BookmarkDto> BOOKMARK_CREATION_DATE_COMPARATOR = new Comparator<BookmarkDto>() {

		public int compare(@NonNull BookmarkDto bookmark1, @NonNull BookmarkDto bookmark2) {
			// descending order
			return bookmark2.createdOn.compareTo(bookmark1.createdOn);
		}
	};
}
