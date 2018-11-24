package net.bible.android.control.report;

import net.bible.android.control.email.Emailer;

public class EmailerStub implements Emailer {

	private String emailDialogTitle;
	private String recipient;
	private String subject;
	private String text;

	@Override
	public void send(String emailDialogTitle, String subject, String text) {
		// TODO Auto-generated method stub

	}

	@Override
	public void send(String emailDialogTitle, String recipient, String subject, String text) {
		this.emailDialogTitle = emailDialogTitle;
		this.recipient = recipient;
		this.subject = subject;
		this.text = text;
	}

	public String getEmailDialogTitle() {
		return emailDialogTitle;
	}

	public String getRecipient() {
		return recipient;
	}

	public String getSubject() {
		return subject;
	}

	public String getText() {
		return text;
	}
}
