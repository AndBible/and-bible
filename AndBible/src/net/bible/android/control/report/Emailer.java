package net.bible.android.control.report;

public interface Emailer {

	public abstract void send(String emailDialogTitle, String subject, String text);

	public abstract void send(String emailDialogTitle, String recipient, String subject, String text);
}