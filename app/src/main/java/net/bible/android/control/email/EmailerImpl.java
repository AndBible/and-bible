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

package net.bible.android.control.email;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import net.bible.android.control.ApplicationScope;
import net.bible.android.view.activity.base.CurrentActivityHolder;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

/**
 * Jump straight to email program with subject, body, recipient, etc
 */
@ApplicationScope
public class EmailerImpl implements Emailer {

	@Inject
	public EmailerImpl() {
	}

	/* (non-Javadoc)
		 * @see net.bible.android.control.report.Email#send(java.lang.String, java.lang.String, java.lang.String)
		 */
	@Override
	public void send(String emailDialogTitle, String subject, String text) {
		send(emailDialogTitle, null, subject, text);
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.control.report.Email#send(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void send(String emailDialogTitle, String recipient, String subject, String body) {

        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SENDTO);
		if (StringUtils.isNotBlank(recipient)) {
	        emailIntent.setData(Uri.fromParts("mailto", recipient, null));
		}

        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);

        Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
        activity.startActivity(emailIntent);
	}
}
