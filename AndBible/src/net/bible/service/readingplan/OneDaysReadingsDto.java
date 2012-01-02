package net.bible.service.readingplan;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.PassageKeyFactory;

import android.util.Log;

public class OneDaysReadingsDto implements Comparable<OneDaysReadingsDto> {
	int mDay;
	String mReadings;
	Key[] mReadingKeys;
	
	private static final String TAG = "OneDaysReadingsDto";
	
	public OneDaysReadingsDto(int day, String readings) {
		mDay = day;
		mReadings = readings;
	}
	
	@Override
	public String toString() {
		return BibleApplication.getApplication().getString(R.string.rdg_plan_day, mDay);
	}

	@Override
	public int compareTo(OneDaysReadingsDto another) {
		return mDay-another.mDay;
	}
	
	public Key getReadingKey(int no) {
		checkKeysGenerated();
		return mReadingKeys[no]; 
	}
	public int getNumReadings() {
		checkKeysGenerated();
		return mReadingKeys.length; 
	}
	
	private synchronized void checkKeysGenerated() {
		if (mReadingKeys==null) {
			String[] readingArray = mReadings.split(",");
			List<Key> readingKeyList = new ArrayList<Key>();
			for (String reading : readingArray) {
				try {
					readingKeyList.add(PassageKeyFactory.instance().getKey(reading));
				} catch (NoSuchKeyException nsk) {
					Log.e(TAG, "Error getting daily reading passage", nsk);
				}
			}
			
			mReadingKeys = readingKeyList.toArray(new Key[readingKeyList.size()]);
		}
	}
}
