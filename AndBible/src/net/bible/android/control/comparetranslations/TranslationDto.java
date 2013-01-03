package net.bible.android.control.comparetranslations;

import java.io.File;

import org.crosswire.jsword.book.Book;

public class TranslationDto {

	private Book book;
	private String text;
	private File customFontFile;
	
	public TranslationDto(Book book, String text, File customFontFile) {
		this.book = book;
		this.text = text;
		this.customFontFile = customFontFile;		
	}
	
	public Book getBook() {
		return book;
	}
	public String getText() {
		return text;
	}
	public File getCustomFontFile() {
		return customFontFile;
	}
}
