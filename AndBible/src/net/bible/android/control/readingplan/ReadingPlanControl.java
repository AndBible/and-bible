package net.bible.android.control.readingplan;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.control.versification.VersificationConverter;
import net.bible.service.common.CommonUtils;
import net.bible.service.history.HistoryManager;
import net.bible.service.readingplan.OneDaysReadingsDto;
import net.bible.service.readingplan.ReadingPlanDao;
import net.bible.service.readingplan.ReadingPlanInfoDto;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.versification.Versification;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

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
	
	private static final String TAG = "ReadingPlanControl";
	
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

	/** Adjust the start date to Jan 1
	 */
	public void setStartToJan1(ReadingPlanInfoDto plan) {
		// tell the plan to set a start date
		plan.setStartToJan1();
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
	private void setCurrentPlanDay(int day) {
		String planCode = getCurrentPlanCode();
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		prefs.edit()
			.putInt(planCode+READING_PLAN_DAY_EXT, day)
			.commit();
	}

	public long getDueDay(ReadingPlanInfoDto planInfo) {
		Date today = CommonUtils.getTruncatedDate();
		Date startDate = planInfo.getStartdate();
		// on final day, after done the startDate will be null
		if (startDate==null) {
			return 0;
		}
		
		// should not need to round as we use truncated dates, but safety first
		// later found that rounding is necessary (due to DST I think) because 
		// when the clocks went forward the difference became 88.95833 but should have been 89
		double diffInDays = (today.getTime() - startDate.getTime())/(1000.0*60*60*24);
		long diffInWholeDays = Math.round(diffInDays);
		Log.d(TAG, "Days diff between today and start:"+diffInWholeDays);
		
		// if diff is zero then we are on day 1 so add 1
		return diffInWholeDays+1;
	}

	/** mark this day as complete unless it is in the future
	 * if last day then reset plan
	 */
	public int done(ReadingPlanInfoDto planInfo, int day, boolean force) {
		// which day to show next -1 means the user is up to date and can close Reading Plan 
		int nextDayToShow = -1;
		
		// force Done to work for whatever day is passed in, otherwise Done only works for current plan day and ignores other days
		if (force) {
			// for Done to work for non plan day
			setCurrentPlanDay(day);
			
			// normal reading status update is circumvented so mark all as read here
			getReadingStatus(day).setAllRead();
		}
		
		// was this the next reading plan day due whether on schedule or not
		if (getCurrentPlanDay() == day) {
			// do not leave prefs for historic days - we show all historic readings as 'read'
			getReadingStatus(day).delete();
			
			// was this the last day in the plan
			if (readingPlanDao.getNumberOfPlanDays(getCurrentPlanCode()) == day) {
				// last plan day is just Done so clear all plan status
				reset(planInfo);
				nextDayToShow = -1;
			} else {
				// move to next plan day
				int nextDay = incrementCurrentPlanDay();
				
				// if there are no readings scheduled for the next day then mark it as Done and carry on to next next day
				OneDaysReadingsDto nextReadings = getDaysReading(nextDay);
				if (nextReadings.getNumReadings()==0) {
					nextDay = done(planInfo, nextDay, force);
				}

				nextDayToShow = nextDay;
			}
		} else {
			if (planInfo.getNumberOfPlanDays()>day) {
				nextDayToShow = day+1;
			}
		}
		
    	//if user is not behind then do not show Daily Reading screen
    	if (!isDueToBeRead(planInfo, nextDayToShow)) {
    		nextDayToShow = -1;    	}

		
		return nextDayToShow;
	}
	
	
	public boolean isDueToBeRead(ReadingPlanInfoDto planInfo, int day) {
		return getDueDay(planInfo) >= day;
	}
	
	/** increment current day
	 */
	public int incrementCurrentPlanDay() {
		int nextDay = getCurrentPlanDay() + 1;
		setCurrentPlanDay(nextDay);
		
		return nextDay;
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

	/** 
	 * Speak 1 reading and mark as read.  Also convert from ReadingPlan v11n type to v11n type of current Bible.
	 */
	public void speak(int day, int readingNo, Key readingKey) {
		AbstractPassageBook bible = CurrentPageManager.getInstance().getCurrentBible().getCurrentPassageBook();
		Versification documentV11n = bible.getVersification();
		
		VersificationConverter v11nConverter = new VersificationConverter();
		Key convertedPassage =  v11nConverter.convert(readingKey, documentV11n);
		
		List<Key> keyList = new ArrayList<Key>();
		keyList.add(convertedPassage);

		mSpeakControl.speak(bible, keyList, true, false);
		
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

		// if resetting default plan then remove default
		if (plan.getCode().equals(getCurrentPlanCode())) {
			prefsEditor.remove(READING_PLAN);
		}
		
		prefsEditor.remove(plan.getCode()+ReadingPlanInfoDto.READING_PLAN_START_EXT);
		prefsEditor.remove(plan.getCode()+READING_PLAN_DAY_EXT);

		prefsEditor.commit();
	}

	public String getShortTitle() {
		return StringUtils.left(getCurrentPlanCode(), 8);
	}
	
	public String getCurrentDayDescription() {
		if (isReadingPlanSelected()) {
			return getDaysReading(getCurrentPlanDay()).getDayDesc();
		} else {
			return "";
		}
	}
	
	/** keep track of which plan the user has currently.  This can be safely changed and reverted to without losing track
	 */
	private String getCurrentPlanCode() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		String currentPlan = prefs.getString(READING_PLAN, null);
		return currentPlan;
	}
}
