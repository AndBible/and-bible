package net.bible.service.common;

public class ParseException extends AndException {

	private static final long serialVersionUID = 1L;

	public ParseException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public ParseException(String detailMessage) {
		super(detailMessage);
	}

}
