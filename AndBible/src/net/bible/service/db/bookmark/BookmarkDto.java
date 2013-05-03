package net.bible.service.db.bookmark;

import java.util.Date;

import net.bible.android.control.versification.VerseVersificationConverter;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

public class BookmarkDto implements Comparable<BookmarkDto> {
	private Long id;
	private VerseVersificationConverter verseVersificationConverter;
	private Date createdOn;

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Verse getVerse() {
		return verseVersificationConverter.getVerse();
	}
	public Verse getVerse(Versification versification) {
		return verseVersificationConverter.getVerse(versification);
	}
	public void setVerse(Verse verse) {
		this.verseVersificationConverter = new VerseVersificationConverter(verse);
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
		if (verseVersificationConverter==null || verseVersificationConverter.getVerse()==null) {
			result = prime * result;
		} else {
			Verse verse = verseVersificationConverter.getVerse();
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
		if (verseVersificationConverter == null) {
			if (other.verseVersificationConverter != null)
				return false;
		} else if (!verseVersificationConverter.equals(other.verseVersificationConverter))
			return false;
		return true;
	}
	
	@Override
	public int compareTo(BookmarkDto another) {
		assert another!=null;
		return verseVersificationConverter.compareTo(another.verseVersificationConverter);
	}
}
