package net.bible.service.device.speak.event;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

public class SpeakProggressEvent {
    public Book book;
    public Key key;
    public SpeakProggressEvent(Book book, Key key) {
        this.key = key;
        this.book = book;
    }
}
