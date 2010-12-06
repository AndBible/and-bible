package net.bible.service.db.bookmark;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

public class BookmarkDto {
	private Long id;
	private Key key;
	private Book book;

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
	public Book getBook() {
		return book;
	}
	public void setBook(Book book) {
		this.book = book;
	}
}
