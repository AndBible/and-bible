package net.bible.android.view.activity.readingplan;

import net.bible.android.activity.R;
import net.bible.service.readingplan.OneDaysReadingsDto;
import net.bible.service.readingplan.ReadingPlanDto;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

/**
 * A simple adapter which maintains an ArrayList of photo resource Ids. 
 * Each photo is displayed as an image. This adapter supports clearing the
 * list of photos and adding a new photo.
 *
 */
public class DailyReadingExpandableListAdapter extends BaseExpandableListAdapter {
	
	private ReadingPlanDto mReadingPlanDto;
//    // Sample data set.  children[i] contains the children (String[]) for groups[i].
//    private String[] groups = { "People Names", "Dog Names", "Cat Names", "Fish Names" };
//    private String[][] children = {
//            { "Arnold", "Barry", "Chuck", "David" },
//            { "Ace", "Bandit", "Cha-Cha", "Deuce" },
//            { "Fluffy", "Snuggles" },
//            { "Goldy", "Bubbles" }
//    };
    
    private Context mContext;
    public DailyReadingExpandableListAdapter(Context context, ReadingPlanDto readingPlanDto) {
    	mContext = context;
    	mReadingPlanDto = readingPlanDto;
    }
    
    public Object getChild(int groupPosition, int childPosition) {
        return getGroup(groupPosition).getReadingKey(childPosition);
    }

    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    public int getChildrenCount(int groupPosition) {
        return getGroup(groupPosition).getNumReadings();
    }
    
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
    	if (convertView == null) {
            LayoutInflater infalInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = infalInflater.inflate(R.layout.reading_plan_list_item, null);
        }

        TextView textView = (TextView) convertView.findViewById(R.id.text1);
        textView.setText(getChild(groupPosition, childPosition).toString());
        return convertView;
    }

    public OneDaysReadingsDto getGroup(int groupPosition) {
        return mReadingPlanDto.getReadingsList().get(groupPosition);
    }

    public int getGroupCount() {
        return mReadingPlanDto.getReadingsList().size();
    }

    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
    	if (convertView == null) {
            LayoutInflater infalInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = infalInflater.inflate(R.layout.reading_plan_list_item, null);
        }

        TextView textView = (TextView) convertView.findViewById(R.id.text1);
        textView.setText(getGroup(groupPosition).toString());
        return convertView;
    }

    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public boolean hasStableIds() {
        return true;
    }

}
