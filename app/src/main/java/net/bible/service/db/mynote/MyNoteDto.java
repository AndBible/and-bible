package net.bible.service.db.mynote;

import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.android.control.versification.sort.ConvertibleVerseRangeUser;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;

import java.util.Date;

/**
 * DTO for MyNote
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteDto implements ConvertibleVerseRangeUser {
	private Long id;
	private ConvertibleVerseRange convertibleVerseRange;
	private String noteText;
	private Date lastUpdatedOn;
	private Date createdOn;

	/** was this dto retrieved from the db
	 */
	public boolean isNew() {
		return id==null;
	}
	
	public boolean isEmpty() {
		return StringUtils.isEmpty(noteText);
	}

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

	@Override
	public ConvertibleVerseRange getConvertibleVerseRange() {
		return convertibleVerseRange;
	}

	public void setNoteText(String newText) {
		this.noteText = newText;
	}
	
	public String getNoteText() {
		return noteText;
	}
	public Date getLastUpdatedOn() {
		return lastUpdatedOn;
	}
	public void setLastUpdatedOn(Date lastUpdatedOn) {
		this.lastUpdatedOn = lastUpdatedOn;
	}
	public Date getCreatedOn() {
		return createdOn;
	}
	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	@Override
	public String toString() {
		return "MyNoteDto{" +
				"convertibleVerseRange=" + convertibleVerseRange +
				", noteText='" + noteText + '\'' +
				'}';
	}

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
	/*
	 * compare verse and note text
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MyNoteDto other = (MyNoteDto) obj;
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
		if (noteText == null) {
			if (other.noteText != null)
				return false;
		} else if (!noteText.equals(other.noteText))
			return false;

		return true;
	}
}
