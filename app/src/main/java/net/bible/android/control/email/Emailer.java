package net.bible.android.control.email;

public interface Emailer {

	public abstract void send(String emailDialogTitle, String subject, String text);

	public abstract void send(String emailDialogTitle, String recipient, String subject, String text);
}