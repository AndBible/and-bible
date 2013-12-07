package net.bible.service.db.bookmark;

import java.util.Comparator;
import java.util.Date;

import net.bible.android.control.versification.ConvertibleVerse;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

public class BookmarkDto implements Comparable<BookmarkDto> {
	private Long id;
	private ConvertibleVerse convertibleVerse;
	private Date createdOn;

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Verse getVerse() {
		return convertibleVerse.getVerse();
	}
	public Verse getVerse(Versification versification) {
		return convertibleVerse.getVerse(versification);
	}
	public void setVerse(Verse verse) {
		this.convertibleVerse = new ConvertibleVerse(verse);
	}
	public Date getCreatedOn() {
		return createdOn;
	}
	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		if (convertibleVerse==null || convertibleVerse.getVerse()==null) {
			result = prime * result;
		} else {
			Verse verse = convertibleVerse.getVerse();
			result = prime * result + verse.hashCode();
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
		if (convertibleVerse == null) {
			if (other.convertibleVerse != null)
				return false;
		} else if (!convertibleVerse.equals(other.convertibleVerse))
			return false;
		return true;
	}

	@Override
	public int compareTo(BookmarkDto another) {
		return BOOKMARK_BIBLE_ORDER_COMPARATOR.compare(this, another);
	}

	/** Compare by Bible order */
	public static Comparator<BookmarkDto> BOOKMARK_BIBLE_ORDER_COMPARATOR = new Comparator<BookmarkDto>() {

		public int compare(BookmarkDto bookmark1, BookmarkDto bookmark2) {
			// ascending order
			return bookmark1.convertibleVerse.compareTo(bookmark2.convertibleVerse);
		}
	};
	/** Compare by Create date - most recent first */
	public static Comparator<BookmarkDto> BOOKMARK_CREATION_DATE_COMPARATOR = new Comparator<BookmarkDto>() {

		public int compare(BookmarkDto bookmark1, BookmarkDto bookmark2) {
			// descending order
			return bookmark2.createdOn.compareTo(bookmark1.createdOn);
		}
	};
}
