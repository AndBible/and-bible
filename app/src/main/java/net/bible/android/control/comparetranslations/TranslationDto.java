package net.bible.android.control.comparetranslations;

import java.io.File;

import org.crosswire.jsword.book.Book;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
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
