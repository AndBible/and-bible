/**
 * 
 */
package net.bible.service.db.mynote;

import java.util.Date;

import net.bible.android.control.versification.ConvertibleVerse;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

/**
 * DTO for MyNote
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteDto implements Comparable<MyNoteDto> {
	private Long id;
	private ConvertibleVerse convertibleVerse;
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
	public Verse getVerse() {
		return convertibleVerse.getVerse();
	}
	public Verse getVerse(Versification versification) {
		return convertibleVerse.getVerse(versification);
	}
	public void setVerse(Verse verse) {
		this.convertibleVerse = new ConvertibleVerse(verse);
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
		if (convertibleVerse == null) {
			if (other.convertibleVerse != null)
				return false;
		} else if (!convertibleVerse.equals(other.convertibleVerse))
			return false;
		if (noteText == null) {
			if (other.noteText != null)
				return false;
		} else if (!noteText.equals(other.noteText))
			return false;

		return true;
	}
	
	@Override
	public int compareTo(MyNoteDto another) {
		assert another!=null;
		return convertibleVerse.compareTo(another.convertibleVerse);
	}
}
