package net.bible.android.view.activity.base;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentBiblePage;

import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;
import org.crosswire.jsword.versification.Versification;

/**
 * Save and fetch a verse from/to intent extras and othr intent fucntionality
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class IntentHelper {

	public static final String VERSE = "net.bible.android.view.activity.comparetranslations.Verse";

	private static final String TAG = "IntentHelper";

	public Verse getIntentVerseOrDefault(Intent intent) {
		//fetch verse from intent if set
		CurrentBiblePage currentDoc = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible();

		try {
			Bundle extras = intent.getExtras();
			if (extras != null && extras.containsKey(VERSE)) {
				Versification currentV11n = ((SwordBook) currentDoc.getCurrentDocument()).getVersification();
				return VerseFactory.fromString(currentV11n, extras.getString(VERSE));
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse from intent, using default");
		}

		// if we got this far there was no verse in the intent
		return currentDoc.getSingleKey();
	}

	public Intent updateIntentWithVerse(Intent intent, Verse verse) {
		intent.putExtra(VERSE, verse.getOsisID());

		return intent;
	}
}
