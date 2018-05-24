package net.bible.service.device.speak;


import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import java.util.List;

public interface SpeakTextProviderInterface {
    void addTextsToSpeak(Book book, List<Key> keyList, boolean repeat);
    boolean isMoreTextToSpeak();
    String getNextTextToSpeak();
    void pause(float fractionCompleted);
    void rewind();
    void forward();
    void finishedUtterance(String utteranceId);
    void reset();
    void persistState();
    boolean restoreState();
    void clearPersistedState();
    long getTotalChars();
    long getSpokenChars();
}
