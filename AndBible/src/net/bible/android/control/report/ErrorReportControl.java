package net.bible.android.control.report;

import java.io.PrintWriter;
import java.io.StringWriter;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import android.os.Build;

public class ErrorReportControl {
	
	private Emailer emailer;
	
	public ErrorReportControl(Emailer emailer) {
		this.emailer = emailer;
	}

	public void sendErrorReportEmail(Exception e) {
		String text = createErrorText(e);
		
		String title = BibleApplication.getApplication().getString(R.string.report_error);
		String subject = title;
		
		emailer.send(title, "errors.andbible@gmail.com", subject, text);
	}
	
	private String createErrorText(Exception exception) {
		try {
			StringBuilder text = new StringBuilder();
			text.append("And Bible version: ").append(CommonUtils.getApplicationVersionName()).append("\n");
			text.append("Android version: ").append(Build.VERSION.RELEASE).append("\n");
			text.append("Android SDK version: ").append(Build.VERSION.SDK_INT).append("\n");
			text.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
			text.append("Model: ").append(Build.MODEL).append("\n");
			text.append("SD card Mb free: ").append(CommonUtils.getSDCardMegsFree()).append("\n");
			
			final Runtime runtime = Runtime.getRuntime();
			final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
			final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
			text.append("Used memory in Mb: ").append(usedMemInMB).append("\n");
			text.append("max heap memory in Mb: ").append(maxHeapSizeInMB).append("\n");

			if (exception!=null) {
				StringWriter errors = new StringWriter();
				exception.printStackTrace(new PrintWriter(errors));
				text.append("Exception:\n").append(errors.toString());
			}
			
			return text.toString();
		} catch (Exception e) {
			return "Exception occurred preparing error text:"+e.getMessage();
		}
	}

}
