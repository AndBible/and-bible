package net.bible.service.common;

public class AndRuntimeException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AndRuntimeException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public AndRuntimeException(String detailMessage) {
		super(detailMessage);
	}

}
