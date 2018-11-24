package net.bible.service.readingplan;

import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.time.DateUtils;
import org.crosswire.jsword.versification.Versification;

import java.util.Calendar;
import java.util.Date;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ReadingPlanInfoDto {

	private String code;
	private String description;
	private Versification versification;
	private int numberOfPlanDays;
	
	public static final String READING_PLAN_START_EXT = "_start";

	public ReadingPlanInfoDto(String code) {
		this.code = code;
	}

	/** set a persistent start date
	 */
	public void start() {
		startOn(CommonUtils.getTruncatedDate(), false);
	}
	
	public void setStartToJan1() {
		Date jan1 = DateUtils.truncate(new Date(), Calendar.YEAR);
		
		startOn(jan1, true);
	}
	
	private void startOn(Date date, boolean force) {

		// if changing plan
		if (getStartdate()==null || force) {
			
			CommonUtils.getSharedPreferences()
						.edit()
						.putLong(code+READING_PLAN_START_EXT, date.getTime())
						.commit();
		}
	}
	
	/** a persistent start date
	 * return the date the plan was started or null if not started
	 */
	public Date getStartdate() {
		Long startDate = CommonUtils.getSharedPreferences().getLong(code+READING_PLAN_START_EXT, 0);
		if (startDate == 0) {
			return null;
		} else {
			return new Date(startDate);
		}
	}
	
	/** set a persistent start date
	 */
	public void reset() {

		// if changing plan
		if (getStartdate()==null) {
			CommonUtils.getSharedPreferences()
						.edit()
						.remove(code+READING_PLAN_START_EXT)
						.commit();
		}
	}

	@Override
	public String toString() {
		return getDescription();
	}
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getDescription() {
		return description;
	}
	public void setTitle(String description) {
		this.description = description;
	}

	public int getNumberOfPlanDays() {
		return numberOfPlanDays;
	}
	public void setNumberOfPlanDays(int numberOfPlanDays) {
		this.numberOfPlanDays = numberOfPlanDays;
	}

	public Versification getVersification() {
		return versification;
	}
	public void setVersification(Versification versification) {
		this.versification = versification;
	}
}
