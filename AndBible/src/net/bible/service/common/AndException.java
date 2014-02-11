package net.bible.service.common;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AndException extends Exception {

	private static final long serialVersionUID = 1L;

	public AndException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public AndException(String detailMessage) {
		super(detailMessage);
	}

}
