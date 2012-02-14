package net.bible.android.control.comparetranslations;

import org.crosswire.jsword.book.Book;

public class TranslationDto {

	private Book book;
	private String text;
	
	public TranslationDto(Book book, String text) {
		this.book = book;
		this.text = text;
	}
	
	public Book getBook() {
		return book;
	}
	public String getText() {
		return text;
	}
}
