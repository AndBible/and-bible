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
	private ReadingPlanInfoDto mReadingPlanInfoDto;
	private int mDay;
	private String mReadings;
	private List<Key> mReadingKeys;
	
	private static final String TAG = "OneDaysReadingsDto";
	
	public OneDaysReadingsDto(int day, String readings, ReadingPlanInfoDto readingPlanInfo) {
		mDay = day;
		mReadings = readings;
		mReadingPlanInfoDto = readingPlanInfo;
	}
	
	@Override
	public String toString() {
		return getDayDesc();
	}

	@Override
	public int compareTo(OneDaysReadingsDto another) {
		return mDay-another.mDay;
	}
	
	public String getDayDesc() {
		return BibleApplication.getApplication().getString(R.string.rdg_plan_day, mDay);
	}
	
	public String getReadingsDesc() {
		checkKeysGenerated();
		StringBuffer readingsBuff = new StringBuffer();
		for (int i=0; i<mReadingKeys.size(); i++) {
			if (i>0) {
				readingsBuff.append(", ");
			}
			readingsBuff.append(mReadingKeys.get(i).getName());
		}
		return readingsBuff.toString(); 
	}

	public Key getReadingKey(int no) {
		checkKeysGenerated();
		return mReadingKeys.get(no); 
	}
	public int getNumReadings() {
		checkKeysGenerated();
		return mReadingKeys.size(); 
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
			
			mReadingKeys = readingKeyList;
		}
	}

	public ReadingPlanInfoDto getReadingPlanInfo() {
		return mReadingPlanInfoDto;
	}

	public List<Key> getReadingKeys() {
		return mReadingKeys;
	}
}
