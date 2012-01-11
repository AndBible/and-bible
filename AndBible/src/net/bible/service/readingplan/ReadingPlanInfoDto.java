package net.bible.service.readingplan;

import java.util.Date;

import net.bible.service.common.CommonUtils;


public class ReadingPlanInfoDto {

	private String code;
	private String description;
	private int numberOfPlanDays;

	public static final String READING_PLAN_START_EXT = "_start";

	public ReadingPlanInfoDto(String code) {
		this.code = code;
	}

	/** set a persistent start date
	 */
	public void start() {

		// if changing plan
		if (getStartdate()==null) {
			CommonUtils.getSharedPreferences()
						.edit()
						.putLong(code+READING_PLAN_START_EXT, CommonUtils.getTruncatedDate().getTime())
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
}
