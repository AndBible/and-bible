/**
 * 
 */
package net.bible.service.db.mynote;

import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.passage.Key;

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
	private Key key;
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
	public Key getKey() {
		return key;
	}
	public void setKey(Key key) {
		this.key = key;
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
		result = prime * result + ((key == null) ? 0 : key.hashCode());
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
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
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
		return key.compareTo(another.key);
	}
}
