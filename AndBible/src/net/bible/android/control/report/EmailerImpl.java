package net.bible.android.control.report;

import net.bible.android.view.activity.base.CurrentActivityHolder;

import org.apache.commons.lang.StringUtils;

import android.app.Activity;
import android.content.Intent;

public class EmailerImpl implements Emailer {

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
	public void send(String emailDialogTitle, String recipient, String subject, String text) {
		Intent sendIntent  = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("text/plain");

		if (StringUtils.isNotBlank(recipient)) {
			sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});	
		}
		
		sendIntent.putExtra(Intent.EXTRA_TEXT, text);
		// subject is used when user chooses to send verse via e-mail
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

		Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
		activity.startActivity(Intent.createChooser(sendIntent, emailDialogTitle)); 
	}
}
