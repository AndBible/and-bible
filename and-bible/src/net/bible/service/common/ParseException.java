package net.bible.service.common;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ParseException extends AndException {

	private static final long serialVersionUID = 1L;

	public ParseException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public ParseException(String detailMessage) {
		super(detailMessage);
	}

}
