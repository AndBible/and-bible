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
