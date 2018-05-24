package net.bible.service.device.speak;

import net.bible.service.sword.SwordContentFacade;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import java.util.List;

public class SpeakBibleTextProvider implements SpeakTextProviderInterface {
    private final SwordContentFacade swordContentFacade;

    SpeakBibleTextProvider(SwordContentFacade swordContentFacade) {
		this.swordContentFacade = swordContentFacade;
	}

    public void addTextsToSpeak(Book book, List<Key> keyList, boolean queue, boolean repeat) {

    }

    public boolean isMoreTextToSpeak() {
        return false;
    }

    public String getNextTextToSpeak() {
        return "Test";
    }

    public void pause(float fractionCompleted) {

    }

    public void rewind() {

    }

    public void forward() {

    }

    public void finishedUtterance(String utteranceId) {

    }

    public void reset() {

    }

    public void persistState() {

    }

    public boolean restoreState() {
        return false;
    }

    public void clearPersistedState() {

    }

    public long getTotalChars() {
        return 0;
    }

    public long getSpokenChars() {
        return 0;
    }
}
