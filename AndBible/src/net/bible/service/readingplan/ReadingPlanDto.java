package net.bible.service.readingplan;

import java.util.ArrayList;
import java.util.List;

public class ReadingPlanDto {

	private String title;
	private List<OneDaysReadingsDto> readingsList = new ArrayList<OneDaysReadingsDto>();

	@Override
	public String toString() {
		return getTitle();
	}
	
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public List<OneDaysReadingsDto> getReadingsList() {
		return readingsList;
	}
	public void setReadingsList(List<OneDaysReadingsDto> readingsList) {
		this.readingsList = readingsList;
	}
}
