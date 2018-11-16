package net.bible.android.control.readingplan;

import java.util.BitSet;

import net.bible.service.common.CommonUtils;
import android.content.SharedPreferences;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ReadingStatus {

    private String planCode;
    private int day;
    private int numReadings;
    
    // there won't be any more than 10 readings per day in any plan
    private BitSet status = new BitSet(4);
    private static final char ONE = '1';
    private static final char ZERO = '0';

    public ReadingStatus(String planCode, int day, int numReadings) {
        super();
        this.planCode = planCode;
        this.day = day;
        this.numReadings = numReadings;
        reloadStatus();
    }

    public void setRead(int readingNo) {
        status.set(readingNo);
        saveStatus();
    }
    
    public boolean isRead(int readingNo) {
        return status.get(readingNo);
    }
    public void setAllRead() {
        for (int i=0; i<numReadings; i++) {
            setRead(i);
        }
        saveStatus();
    }
    public boolean isAllRead() {
        for (int i=0; i<numReadings; i++) {
            if (!isRead(i)) {
                return false;
            }
        }
        return true;
    }

    /** do not leave prefs around for historic days
     */
    public void delete() {
        SharedPreferences prefs = CommonUtils.getSharedPreferences();
        if (prefs.contains(getPrefsKey())) {
            prefs.edit()
                .remove(getPrefsKey())
                .commit();
        }
    }
    
    /** read status from prefs string
     */
    public void reloadStatus() {
        SharedPreferences prefs = CommonUtils.getSharedPreferences();
        String gotStatus = prefs.getString(getPrefsKey(), "");
        for (int i=0; i<gotStatus.length(); i++) {
            if (gotStatus.charAt(i)==ONE) {
                status.set(i);
            } else {
                status.clear(i);
            }
        }
    }

    /** serialize read status to prefs in a string
     */
    private void saveStatus() {
        StringBuffer strStatus = new StringBuffer();
        for (int i=0; i<status.length(); i++) {
            if (status.get(i)) {
                strStatus.append(ONE);
            } else {
                strStatus.append(ZERO);
            }
        }
        SharedPreferences prefs = CommonUtils.getSharedPreferences();
        prefs.edit()
            .putString(getPrefsKey(), strStatus.toString())
            .commit();
    }
    
    protected String getPlanCode() {
        return planCode;
    }

    protected int getDay() {
        return day;
    }

    private String getPrefsKey() {
        return planCode+"_"+day;
    }
    
}
