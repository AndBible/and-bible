package net.bible.service.device.speak;

import android.util.Log;
import android.util.Pair;
import de.greenrobot.event.EventBus;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentBibleVerse;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.service.common.ParseException;
import net.bible.service.device.speak.event.SpeakProggressEvent;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class SpeakBibleTextProvider extends AbstractSpeakTextProvider {
    private final SwordContentFacade swordContentFacade;
    private static final String TAG = "Speak";
    private final SwordDocumentFacade swordDocumentFacade;
    private final BibleTraverser bibleTraverser;

    private List<Pair<Book, Verse>> itemList;
    private Pair<Book, Verse> currentItem = null;
    private boolean continuous = false;

    SpeakBibleTextProvider(SwordContentFacade swordContentFacade, BibleTraverser bibleTraverser,
                           SwordDocumentFacade swordDocumentFacade) {
		this.swordContentFacade = swordContentFacade;
		this.swordDocumentFacade = swordDocumentFacade;
		this.bibleTraverser = bibleTraverser;
		itemList = new ArrayList<>();
	}

    public void addTextsToSpeak(Book book, List<Key> keyList, HashMap<String, Boolean> settings) {
        continuous = settings.get("continuous");
        itemList.addAll(toPairs(book, keyList));
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
        return itemList.size() != 0;
    }

    public String getNextTextToSpeak() {
        String text = "";
        while(text.length() == 0) {
            if (currentItem == null) {
                currentItem = itemList.get(0);
                itemList.remove(0);
            }
            else if (continuous && currentItem != null) {
                CurrentBiblePage currentBiblePage = new CurrentBiblePage(new CurrentBibleVerse(), bibleTraverser, swordContentFacade, swordDocumentFacade);
                currentBiblePage.setKey(currentItem.second);
                currentBiblePage.setCurrentDocument(currentItem.first);
                currentBiblePage.doNextVerse();
                currentItem = new Pair<>(currentItem.first, (Verse) currentBiblePage.getSingleKey());
            }
            text = getTextForCurrentItem();
        }

        EventBus.getDefault().post(new SpeakProggressEvent(currentItem.first, currentItem.second));
        return text;
    }

    private String getTextForCurrentItem() {
        try {
            String text = swordContentFacade.getTextToSpeak(currentItem.first, currentItem.second);
            if(text.length() != 0) {
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
        return "";
    }
/*
    private bool setCurrentItemFromList() {
        while(itemList.size() > 0) {
            if (currentItem == null) {
                currentItem = itemList.get(0);
                itemList.remove(0);
                return true;
            }
        }
        return false;
    }
*/
    public void pause(float fractionCompleted) {

    }

    public void rewind() {

    }

    public void forward() {

    }

    public void finishedUtterance(String utteranceId) {
        //currentItem = null;
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
