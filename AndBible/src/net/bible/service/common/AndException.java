package net.bible.service.common;

public class AndException extends Exception {

	private static final long serialVersionUID = 1L;

	public AndException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public AndException(String detailMessage) {
		super(detailMessage);
	}

}
