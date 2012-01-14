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
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ReadingPlanControl {

	private ReadingPlanDao readingPlanDao = new ReadingPlanDao();
	private SpeakControl mSpeakControl;
	
	private static final String READING_PLAN = "reading_plan";
	private static final String READING_PLAN_DAY_EXT = "_day";
	
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
		// set default plan
		setReadingPlan(plan.getCode());
		
		// tell the plan to set a start date
		plan.start();
	}

	/** change default plan
	 */
	public void setReadingPlan(String planCode) {
		// set default plan to this
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		prefs.edit()
			.putString(READING_PLAN, planCode)
			.commit();
	}
	
	/** get list of days and readings for a plan so user can see the plan in advance
	 */
	public List<OneDaysReadingsDto> getCurrentPlansReadingList() {
		return readingPlanDao.getReadingList(getCurrentPlanCode());
		
	}
	
	/** get read status of this days readings
	 */
	public ReadingStatus getReadingStatus(int day) {
		String planCode = getCurrentPlanCode();
		
		if (readingStatus==null || 
			!readingStatus.getPlanCode().equals(planCode) ||
			readingStatus.getDay() != day) {
			OneDaysReadingsDto oneDaysReadingsDto = readingPlanDao.getReading(planCode, day);
			// if Historic then return historic status that returns read=true for all passages
			if (day<getCurrentPlanDay()) {
				readingStatus = new HistoricReadingStatus(getCurrentPlanCode(), day, oneDaysReadingsDto.getNumReadings());
			} else {
				readingStatus = new ReadingStatus(getCurrentPlanCode(), day, oneDaysReadingsDto.getNumReadings());
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

	public long getDueDay(ReadingPlanInfoDto planInfo) {
		Date today = CommonUtils.getTruncatedDate();
		Date startDate = planInfo.getStartdate();
		// should not need to round as we use truncated dates, but safety first
		int diffInDays = Math.round(today.getTime() - startDate.getTime())/(1000*60*60*24);
		
		// if diff is zero then we are on day 1 so add 1
		return diffInDays+1;
	}

	/** mark this day as complete unless it is in the future
	 * if last day then reset plan
	 */
	public void done(ReadingPlanInfoDto planInfo, int day) {
		
		if (getCurrentPlanDay() == day) {
			// do not leave prefs for historic days - we show all historic readings as 'read'
			getReadingStatus(day).delete();
			
			if (readingPlanDao.getNumberOfPlanDays(getCurrentPlanCode()) == day) {
				// last plan day is just Done
				reset(planInfo);
			} else if (getCurrentPlanDay()==day){
				// move to next plan day
				incrementCurrentPlanDay();
			}
		}
	}
	
	
	public boolean isDueToBeRead(ReadingPlanInfoDto planInfo, int day) {
		return getDueDay(planInfo) >= day;
	}
	
	/** increment current day
	 */
	public void incrementCurrentPlanDay() {
		String planCode = getCurrentPlanCode();
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		int day = prefs.getInt(planCode+READING_PLAN_DAY_EXT, 1);
		
		prefs.edit()
			.putInt(getCurrentPlanCode()+READING_PLAN_DAY_EXT, day+1)
			.commit();
	}

	/** get readings due for current plan on specified day
	 */
	public OneDaysReadingsDto getDaysReading(int day) {
		return readingPlanDao.getReading(getCurrentPlanCode(), day);
	}
	
	/** User wants to read a passage from the daily reading
	 * Also mark passage as read
	 */
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
	
	/** User wants all passages from the daily reading spoken using TTS
	 * Also mark passages as read
	 */
	public void speak(int day, List<Key> allReadings) {
		mSpeakControl.speak(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), allReadings, true, false);

		// mark all readings as read
		for (int i=0; i<allReadings.size(); i++) {
			getReadingStatus(day).setRead(i);
		}
	}

	/** IOC
	 */
	public void setSpeakControl(SpeakControl speakControl) {
		this.mSpeakControl = speakControl;
	}
	
	/** User has chosen to start a plan
	 */
	public void reset(ReadingPlanInfoDto plan) {
		plan.reset();
		
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		Editor prefsEditor = prefs.edit();

		// if changing plan
		if (plan.equals(getCurrentPlanCode())) {
			prefsEditor.remove(READING_PLAN);
		}
		
		prefsEditor.remove(plan.getCode()+ReadingPlanInfoDto.READING_PLAN_START_EXT);
		prefsEditor.remove(plan.getCode()+READING_PLAN_DAY_EXT);

		prefsEditor.commit();
	}

	/** keep track of which plan the user has currently.  This can be safely changed and reverted to without losing track
	 */
	private String getCurrentPlanCode() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		String currentPlan = prefs.getString(READING_PLAN, null);
		return currentPlan;
	}
}
