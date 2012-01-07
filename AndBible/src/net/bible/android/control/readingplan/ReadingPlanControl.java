package net.bible.android.control.readingplan;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.speak.SpeakControl;
import net.bible.service.common.CommonUtils;
import net.bible.service.history.HistoryManager;
import net.bible.service.readingplan.OneDaysReadingsDto;
import net.bible.service.readingplan.ReadingPlanDao;
import net.bible.service.readingplan.ReadingPlanInfoDto;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/** Control status of reading plans
 * 
 * @author denha1m
 *
 */
public class ReadingPlanControl {

	private ReadingPlanDao readingPlanDao = new ReadingPlanDao();
	private SpeakControl mSpeakControl;
	
	private static final String READING_PLAN = "reading_plan";
	private static final String READING_PLAN_DAY_EXT = "_day";
	private static final String READING_PLAN_START_EXT = "_start";
	
	private ReadingStatus readingStatus;
	
	/** allow front end to determine if a plan needs has been selected
	 */
	public boolean isReadingPlanSelected() {
		return StringUtils.isNotEmpty(getCurrentPlanCode());
	}
	
	/** get a list of plans so the user can choose one
	 */
	public List<ReadingPlanInfoDto> getReadingPlanList() {
		return readingPlanDao.getReadingPlanList();
	}

	/** User has chosen to start a plan
	 */
	public void startReadingPlan(ReadingPlanInfoDto plan) {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		String currentPlan = prefs.getString(READING_PLAN, null);

		// if changing plan
		if (!plan.getCode().equals(currentPlan)) {
			Editor editablePrefs = prefs.edit();
			editablePrefs.putString(READING_PLAN, plan.getCode());
			editablePrefs.putLong(plan.getCode()+READING_PLAN_START_EXT, new Date().getTime());
			editablePrefs.commit();
		}
	}

	/** get list of days and readings for a plan so user can see the plan in advance
	 */
	public List<OneDaysReadingsDto> getCurrentPlansReadingList() {
		return readingPlanDao.getReadingList(getCurrentPlanCode());
		
	}
	
	/** get read status of this days readings
	 */
	public ReadingStatus getReadingStatus(int day) {
		if (readingStatus==null || 
			!readingStatus.getPlanCode().equals(getCurrentPlanCode()) ||
			readingStatus.getDay() != day) {
			// if Historic then return historic status that returns read=true for all passages
			if (day<getCurrentPlanDay()) {
				readingStatus = new HistoricReadingStatus(getCurrentPlanCode(), day);
			} else {
				readingStatus = new ReadingStatus(getCurrentPlanCode(), day);
			}
		}
		return readingStatus;
	}
	
	public int getCurrentPlanDay() {
		String planCode = getCurrentPlanCode();
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		int day = prefs.getInt(planCode+READING_PLAN_DAY_EXT, 1);
		return day;
	}

	public void completed(int day) {
		if (getCurrentPlanDay() >= day) {
			// do not leave prefs for historic days - we show all historic readings as 'read'
			getReadingStatus(day).delete();
			
			if (readingPlanDao.getNumberOfPlanDays(getCurrentPlanCode()) == day) {
				resetCurrentPlan();
			} else {
				setCurrentPlanDay(day+1);
			}
		}
	}
	
	public void setCurrentPlanDay(int newDay) {
		String planCode = getCurrentPlanCode();
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		int day = prefs.getInt(planCode+READING_PLAN_DAY_EXT, 1);
		if (day != newDay) {
			Editor editablePrefs = prefs.edit();
			editablePrefs.putInt(getCurrentPlanCode()+READING_PLAN_DAY_EXT, newDay);
			editablePrefs.commit();
		}
	}

	public OneDaysReadingsDto getDaysReading(int day) {
		return readingPlanDao.getReading(getCurrentPlanCode(), day);
	}
	
	public void read(int day, int readingNo, Key readingKey) {
    	if (readingKey!=null) {
    		// mark reading as 'read'
    		getReadingStatus(day).setRead(readingNo);

			HistoryManager.getInstance().beforePageChange();

			// show the current bible
    		Book doc = CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument();
    		// go to correct passage
    		CurrentPageManager.getInstance().setCurrentDocumentAndKey(doc, readingKey);
    	}
	}

	/** speak 1 reading and mark as read
	 */
	public void speak(int day, int readingNo, Key readingKey) {
		List<Key> keyList = new ArrayList<Key>();
		keyList.add(readingKey);
		mSpeakControl.speak(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), keyList, true, false);
		
		getReadingStatus(day).setRead(readingNo);
	}
	
	public void speak(int day, List<Key> allReadings) {
		mSpeakControl.speak(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), allReadings, true, false);

		// mark all readings as read
		for (int i=0; i<allReadings.size(); i++) {
			getReadingStatus(day).setRead(i);
		}
	}

	public void setSpeakControl(SpeakControl speakControl) {
		this.mSpeakControl = speakControl;
	}
	
	/** User has chosen to start a plan
	 */
	private void resetCurrentPlan() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		String currentPlan = prefs.getString(READING_PLAN, null);

		// if changing plan
		if (!StringUtils.isEmpty(currentPlan)) {
			prefs.edit()
				.remove(READING_PLAN)
				.remove(currentPlan+READING_PLAN_START_EXT)
				.remove(currentPlan+READING_PLAN_DAY_EXT)
				.commit();
		}
	}

	private String getCurrentPlanCode() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		String currentPlan = prefs.getString(READING_PLAN, null);
		return currentPlan;
	}
}
