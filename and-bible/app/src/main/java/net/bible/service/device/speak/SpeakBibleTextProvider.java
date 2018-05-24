package net.bible.service.device.speak;

import android.util.Log;
import android.util.Pair;
import de.greenrobot.event.EventBus;
import net.bible.service.common.ParseException;
import net.bible.service.device.speak.event.SpeakProggressEvent;
import net.bible.service.sword.SwordContentFacade;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;

import java.util.ArrayList;
import java.util.List;


public class SpeakBibleTextProvider extends AbstractSpeakTextProvider {
    private final SwordContentFacade swordContentFacade;
    private static final String TAG = "Speak";

    private List<Pair<Book, Key>> itemList;
    private Pair<Book, Key> currentItem = null;

    SpeakBibleTextProvider(SwordContentFacade swordContentFacade) {
		this.swordContentFacade = swordContentFacade;
		itemList = new ArrayList<>();
	}

    public void addTextsToSpeak(Book book, List<Key> keyList, boolean repeat) {
        itemList.addAll(toPairs(book, keyList));
    }

    static private List<Pair<Book, Key>> toPairs(Book book, List<Key> keyList) {
        List<Pair<Book, Key>> itemPairs = new ArrayList<>();
        for(Key key: keyList) {
            // Expand verse ranges
            if(key instanceof VerseRange) {
                Verse[] verses = ((VerseRange) key).toVerseArray();
                for (Verse verse : verses) {
                    itemPairs.add(new Pair<>(book, (Key) verse));
                }
            }
            else {
                itemPairs.add(new Pair<>(book, key));
            }
        }
        return itemPairs;
    }

    public boolean isMoreTextToSpeak() {
        return itemList.size() != 0;
    }

    public String getNextTextToSpeak() {
        while(itemList.size() > 0) {
            if (currentItem == null) {
                currentItem = itemList.get(0);
                itemList.remove(0);
            }
            try {
                String text = swordContentFacade.getTextToSpeak(currentItem.first, currentItem.second);
                if(text.length() != 0) {
                    EventBus.getDefault().post(new SpeakProggressEvent(currentItem.first, currentItem.second));
                    return text;
                }
                else {
                    currentItem = null;
                }
            } catch (NoSuchKeyException | BookException | ParseException e) {
                Log.e(TAG, "Error in getting text to speak");
                e.printStackTrace();
                return "";
            }
        }
        return "";
    }

    public void pause(float fractionCompleted) {

    }

    public void rewind() {

    }

    public void forward() {

    }

    public void finishedUtterance(String utteranceId) {
        currentItem = null;
    }

    public void reset() {
        itemList.clear();
        currentItem = null;
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
