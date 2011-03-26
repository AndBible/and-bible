package example.tablekeyboard;

import android.content.Context;
import android.content.res.Configuration;

public class CommonUtils {
	static Context context;
	
    public static boolean isPortrait() {
    	return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

}
