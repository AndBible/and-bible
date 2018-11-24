/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.report;

import android.os.Build;

import net.bible.android.activity.R;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.email.Emailer;
import net.bible.service.common.CommonUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.inject.Inject;

@ApplicationScope
public class ErrorReportControl {
	
	private final Emailer emailer;

	private final ResourceProvider resourceProvider;

	@Inject
	public ErrorReportControl(Emailer emailer, ResourceProvider resourceProvider) {
		this.emailer = emailer;
		this.resourceProvider = resourceProvider;
	}

	public void sendErrorReportEmail(Exception e) {
		String text = createErrorText(e);
		
		String title = resourceProvider.getString(R.string.report_error);
		String subject = getSubject(e, title);
		
		emailer.send(title, "errors.andbible@gmail.com", subject, text);
	}
	
	private String createErrorText(Exception exception) {
		try {
			StringBuilder text = new StringBuilder();
			text.append("And Bible version: ").append(CommonUtils.getApplicationVersionName()).append("\n");
			text.append("Android version: ").append(Build.VERSION.RELEASE).append("\n");
			text.append("Android SDK version: ").append(Build.VERSION.SDK_INT).append("\n");
			text.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
			text.append("Model: ").append(Build.MODEL).append("\n\n");
			text.append("SD card Mb free: ").append(CommonUtils.getSDCardMegsFree()).append("\n\n");
			
			final Runtime runtime = Runtime.getRuntime();
			final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
			final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
			text.append("Used heap memory in Mb: ").append(usedMemInMB).append("\n");
			text.append("Max heap memory in Mb: ").append(maxHeapSizeInMB).append("\n\n");

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

	private String getSubject(Exception e, String title) {
		if (e==null || e.getStackTrace().length==0) {
			return title;
		}
		
		StackTraceElement[] stack = e.getStackTrace();
		for (StackTraceElement elt : stack) {
			if (elt.getClassName().contains("net.bible")) {
				return e.getMessage()+":"+elt.getClassName()+"."+elt.getMethodName()+":"+elt.getLineNumber();
			}
		}
		
		return e.getMessage();
	}
}

