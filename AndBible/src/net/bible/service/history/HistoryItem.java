package net.bible.service.history;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

public class HistoryItem {
	enum eHistoryChangeType { PASSAGE, DOCUMENT};
	
	private Key passage;
	private Book document;
}
