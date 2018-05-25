package net.bible.service.device.speak;

import android.util.Log;
import android.util.Pair;
import de.greenrobot.event.EventBus;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentBibleVerse;
import net.bible.android.control.speak.SpeakSettings;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.service.common.ParseException;
import net.bible.service.device.speak.event.SpeakProggressEvent;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;

import java.util.ArrayList;
import java.util.List;


public class SpeakBibleTextProvider extends AbstractSpeakTextProvider {
    private final SwordContentFacade swordContentFacade;
    private static final String TAG = "Speak";
    private final BibleTraverser bibleTraverser;

    private Pair<Book, Verse> currentItem = null;
    private boolean itemRead = false;
    private boolean continuous = false;

    SpeakBibleTextProvider(SwordContentFacade swordContentFacade, BibleTraverser bibleTraverser) {
		this.swordContentFacade = swordContentFacade;
		this.bibleTraverser = bibleTraverser;
	}

    void setupReading(Book book, Verse verse, SpeakSettings settings) {
        continuous = settings.getContinuous();
        currentItem = new Pair<>(book, verse);
        itemRead = false;
    }

    static private List<Pair<Book, Verse>> toPairs(Book book, List<Key> keyList) {
        List<Pair<Book, Verse>> itemPairs = new ArrayList<>();
        for(Key key: keyList) {
            // Expand verse ranges
            if(key instanceof VerseRange) {
                Verse[] verses = ((VerseRange) key).toVerseArray();
                for (Verse verse : verses) {
                    itemPairs.add(new Pair<>(book, verse));
                }
            }
            else if(key instanceof Verse) {
                itemPairs.add(new Pair<>(book, (Verse)key));
            }
            else {
                Log.e(TAG, "Wrong type!");
            }
        }
        return itemPairs;
    }

    public boolean isMoreTextToSpeak() {
        return currentItem != null;
    }

    public String getNextTextToSpeak() {
        // TODO: speak chapter / book changes
        // TODO option to speak subtitles
        
        String text = "";
        if(currentItem == null) {
            return text;
        }
        if (itemRead) {
            forward();
        }
        text = getTextForCurrentItem();

        // Skip empty verses
        while(text.length() <= 0) {
            forward();
            text = getTextForCurrentItem();
        }

        EventBus.getDefault().post(new SpeakProggressEvent(currentItem.first, currentItem.second));
        return text;
    }

    private String getTextForCurrentItem() {
        try {
            return swordContentFacade.getTextToSpeak(currentItem.first, currentItem.second);
        } catch (NoSuchKeyException | BookException | ParseException e) {
            Log.e(TAG, "Error in getting text to speak");
            e.printStackTrace();
            return "";
        }
    }

    public void pause(float fractionCompleted) {

    }

    public void rewind() {
        Verse prevVerse = bibleTraverser.getPrevVerse((AbstractPassageBook)currentItem.first, currentItem.second);
        currentItem = new Pair<>(currentItem.first, prevVerse);
        itemRead = false;
    }

    public void forward() {
        Verse nextVerse = bibleTraverser.getNextVerse((AbstractPassageBook)currentItem.first, currentItem.second);
        currentItem = new Pair<>(currentItem.first, nextVerse);
        itemRead = false;
    }

    public void finishedUtterance(String utteranceId) {
        itemRead = true;
    }

    public void reset() {
        currentItem = null;
        itemRead = false;
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
