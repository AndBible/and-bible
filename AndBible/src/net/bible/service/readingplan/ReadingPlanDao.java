package net.bible.service.readingplan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import net.bible.android.BibleApplication;
import net.bible.service.common.AndRuntimeException;

import org.apache.commons.lang.StringUtils;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

public class ReadingPlanDao {

	private static final String READING_PLAN_FOLDER = "readingplan";
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

	public int getNumberOfPlanDays(String planCode) {
		return getPlanProperties(planCode).size();
	}

	private ReadingPlanInfoDto getReadingPlanInfoDto(String planCode) {
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
	
	private List<String> getAllReadingPlanCodes() throws IOException {
		
		Resources resources = BibleApplication.getApplication().getResources();
		AssetManager assetManager = resources.getAssets();

		String[] files = assetManager.list(READING_PLAN_FOLDER);
		List<String> codes = new ArrayList<String>();
		for (String file : files) {
			// this if statement ensures we only deal with .properties files - not folders or anything else
			if (file.endsWith(DOT_PROPERTIES)) {
				// remove the file extension to get the code
				codes.add(file.replace(DOT_PROPERTIES, ""));
			}
		}
		
		return codes;
	}

	private Properties getPlanProperties(String planCode) {
		Resources resources = BibleApplication.getApplication().getResources();
		AssetManager assetManager = resources.getAssets();

		// Read from the /assets directory
	    Properties properties = new Properties();
		try {
		    InputStream inputStream = assetManager.open(READING_PLAN_FOLDER+File.separator+planCode+DOT_PROPERTIES);
		    properties.load(inputStream);
		    Log.d(TAG, "The properties are now loaded");
		    Log.d(TAG, "properties: " + properties);
		} catch (IOException e) {
		    System.err.println("Failed to open reading plan property file");
		    e.printStackTrace();
		}
		return properties;
	}
	
}
