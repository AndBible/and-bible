package net.bible.android.control.readingplan;

/** return isRead' for all historical readings
 */
public class HistoricReadingStatus extends ReadingStatus {

	public HistoricReadingStatus(String planCode, int day) {
		super(planCode, day);
	}

	@Override
	public void setRead(int readingNo) {
		// do nothing - all readings are already read
	}

	@Override
	public boolean isRead(int readingNo) {
		// all readings are already read
		return true;
	}

	@Override
	public void delete() {
		// do nothing
	}

	@Override
	public void reloadStatus() {
		// do nothing
	}
	
	
}
