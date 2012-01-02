package net.bible.android.control.readingplan;

import net.bible.service.readingplan.ReadingPlanDao;
import net.bible.service.readingplan.ReadingPlanDto;

public class ReadingPlanControl {

	private ReadingPlanDao readingPlanDao = new ReadingPlanDao();
	
	public ReadingPlanDto getCurrentDailyPlan() {
		return readingPlanDao.getReadingList("y1ot1nt2");
		
	}
}
