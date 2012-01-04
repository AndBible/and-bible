package net.bible.service.readingplan;


public class ReadingPlanInfoDto {

	private String code;
	private String description;
	private int numberOfPlanDays;

	public ReadingPlanInfoDto(String code) {
		this.code = code;
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
