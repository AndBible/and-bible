package net.bible.service.common;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AndRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AndRuntimeException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public AndRuntimeException(String detailMessage) {
        super(detailMessage);
    }

}
