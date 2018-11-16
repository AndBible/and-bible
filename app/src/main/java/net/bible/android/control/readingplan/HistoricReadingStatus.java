package net.bible.android.control.readingplan;

/** return isRead' for all historical readings
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class HistoricReadingStatus extends ReadingStatus {

    public HistoricReadingStatus(String planCode, int day, int numReadings) {
        super(planCode, day, numReadings);
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
