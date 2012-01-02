package net.bible.service.readingplan;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import net.bible.android.BibleApplication;

import org.apache.commons.lang.StringUtils;

import android.content.res.AssetManager;
import android.content.res.Resources;

public class ReadingPlanDao {

	private static final String READING_PLAN_FOLDER = "readingplan/";
	
	public ReadingPlanDto getReadingList(String planName) {
		
		Resources resources = BibleApplication.getApplication().getResources();
		AssetManager assetManager = resources.getAssets();

		// Read from the /assets directory
	    Properties properties = new Properties();
		try {
		    InputStream inputStream = assetManager.open(READING_PLAN_FOLDER+"y1ot1nt2.properties");
		    properties.load(inputStream);
		    System.out.println("The properties are now loaded");
		    System.out.println("properties: " + properties);
		} catch (IOException e) {
		    System.err.println("Failed to open reading plan property file");
		    e.printStackTrace();
		}
		
		ReadingPlanDto plan = new ReadingPlanDto();
		List<OneDaysReadingsDto> list = plan.getReadingsList();
		for (Entry<Object,Object> entry : properties.entrySet()) {
			String key = (String)entry.getKey();
			String value = (String)entry.getValue();
			if (key.equals("title")) {
				plan.setTitle((String)entry.getValue());
			} else if (StringUtils.isNumeric(key)) {
				int day = Integer.parseInt(key);
				list.add(new OneDaysReadingsDto(day, value));
			}
		}
		Collections.sort(list);
		
		return plan;		
	}
}
