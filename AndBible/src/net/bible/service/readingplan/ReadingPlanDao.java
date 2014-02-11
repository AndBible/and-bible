package net.bible.service.readingplan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import net.bible.android.BibleApplication;
import net.bible.android.SharedConstants;
import net.bible.service.common.AndRuntimeException;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.IOUtil;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ReadingPlanDao {

	private String cachedPlanCode = "";
	private Properties cachedPlanProperties;
	
	private static final String READING_PLAN_FOLDER = SharedConstants.READINGPLAN_DIR_NAME;
	private static final File USER_READING_PLAN_FOLDER = SharedConstants.MANUAL_READINGPLAN_DIR;
	private static final String DOT_PROPERTIES = ".properties";
	private static final String TAG = "ReadingPlanDao";

	public List<ReadingPlanInfoDto> getReadingPlanList() {
		try {
			List<String> codes = getAllReadingPlanCodes();
			
			List<ReadingPlanInfoDto> planInfoList = new ArrayList<ReadingPlanInfoDto>();
			for (String code : codes) {
				planInfoList.add(getReadingPlanInfoDto(code));
			}
			
			return planInfoList;
		
		} catch (Exception e) {
			Log.e(TAG, "Error getting reading plans", e);
			throw new AndRuntimeException("Error getting reading plans", e);
		}
	}

	/** get a list of all days readings in a plan
	 */
	public List<OneDaysReadingsDto> getReadingList(String planName) {
		
		ReadingPlanInfoDto planInfo = getReadingPlanInfoDto(planName);
		
		Properties properties = getPlanProperties(planName);
		
		List<OneDaysReadingsDto> list = new ArrayList<OneDaysReadingsDto>();
		for (Entry<Object,Object> entry : properties.entrySet()) {
			String key = (String)entry.getKey();
			String value = (String)entry.getValue();
			if (StringUtils.isNumeric(key)) {
				int day = Integer.parseInt(key);
				OneDaysReadingsDto daysReading = new OneDaysReadingsDto(day, value, planInfo);
				list.add(daysReading);
			}
		}
		Collections.sort(list);
		
		return list;		
	}

	/** get readings for one day
	 */
	public OneDaysReadingsDto getReading(String planName, int dayNo) {
		Properties properties = getPlanProperties(planName);
		
		String readings = (String)properties.get(Integer.toString(dayNo));
		Log.d(TAG, "Readings for day:"+readings);
		return new OneDaysReadingsDto(dayNo, readings, getReadingPlanInfoDto(planName));
	}

	/** get last day number - there may be missed days so cannot simply do props.size()
	 */
	public int getNumberOfPlanDays(String planCode) {
		int maxDayNo = 0;
		
		for(Object oDayNo : getPlanProperties(planCode).keySet() ) {
			String dayNoStr = (String) oDayNo;
			if (StringUtils.isNumeric(dayNoStr)) {
				int dayNo = Integer.parseInt(dayNoStr);
				maxDayNo = Math.max(maxDayNo, dayNo);
			} else {
				Log.e(TAG, "Invalid day number:"+dayNoStr);
			}
		}
		
		return maxDayNo;
	}

	private ReadingPlanInfoDto getReadingPlanInfoDto(String planCode) {
		Log.d(TAG, "Get reading plan info:"+planCode);
		ReadingPlanInfoDto info = new ReadingPlanInfoDto(planCode);
		int id = BibleApplication.getApplication().getResources().getIdentifier("rdg_plan_"+planCode, "string", "net.bible.android.activity");
		String desc = "";
		if (id != 0) {
			desc = BibleApplication.getApplication().getResources().getString(id);
		}
		info.setTitle(desc);
		
		info.setNumberOfPlanDays(getNumberOfPlanDays(planCode));
		return info;
	}

	/** look in assets/readingplan and sdcard/jsword/readingplan for reading plans and return a list of all codes
	 */
	private List<String> getAllReadingPlanCodes() throws IOException {
		
		Resources resources = BibleApplication.getApplication().getResources();
		AssetManager assetManager = resources.getAssets();

		List<String> allCodes = new ArrayList<String>();
		
		String[] internalPlans = assetManager.list(READING_PLAN_FOLDER);
		allCodes.addAll(getReadingPlanCodes(internalPlans));
		
		String[] userPlans = USER_READING_PLAN_FOLDER.list();
		allCodes.addAll(getReadingPlanCodes(userPlans));
		
		return allCodes;
	}

	private List<String> getReadingPlanCodes(String[] files) {
		List<String> codes = new ArrayList<String>();
		if (files!=null) {
			for (String file : files) {
				// this if statement ensures we only deal with .properties files - not folders or anything else
				if (file.endsWith(DOT_PROPERTIES)) {
					// remove the file extension to get the code
					codes.add(file.replace(DOT_PROPERTIES, ""));
				}
			}
		}		
		return codes;
	}

	/* either load reading plan info from assets/readingplan or sdcard/jsword/readingplan
	 */
	private synchronized Properties getPlanProperties(String planCode) {
		if (!planCode.equals(cachedPlanCode)) {
			Resources resources = BibleApplication.getApplication().getResources();
			AssetManager assetManager = resources.getAssets();
			String filename = planCode+DOT_PROPERTIES;
	
			// Read from the /assets directory
		    Properties properties = new Properties();
		    InputStream inputStream = null;
			try {
				// check to see if a user has created his own reading plan with this name
				File userReadingPlanFile = new File(USER_READING_PLAN_FOLDER, filename);
				boolean isUserPlan = userReadingPlanFile.exists();
	
			    if (!isUserPlan) {
			    	inputStream = assetManager.open(READING_PLAN_FOLDER+File.separator+filename);
			    } else {
			    	inputStream = new FileInputStream(userReadingPlanFile);
			    }
			    properties.load(inputStream);
			    Log.d(TAG, "The properties are now loaded");
			    Log.d(TAG, "properties: " + properties);

			    // cache it so we don't constantly reload the properties
			    cachedPlanCode = planCode;
			    cachedPlanProperties = properties;
			    		
			} catch (IOException e) {
			    System.err.println("Failed to open reading plan property file");
			    e.printStackTrace();
			} finally {
				IOUtil.close(inputStream);
			}
		}
		return cachedPlanProperties;
	}
}
