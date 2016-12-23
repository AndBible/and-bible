package net.bible.android.control.event.documentdownload;

import net.bible.android.control.download.DocumentStatus;

/** Event raised when a change related to document download occurs
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentDownloadEvent {

	private DocumentStatus documentStatus;

	public DocumentDownloadEvent(String initials, DocumentStatus.DocumentInstallStatus status, int percentDone) {
		super();
		documentStatus = new DocumentStatus(initials, status, percentDone);
	}

	public DocumentStatus getDocumentStatus() {
		return documentStatus;
	}

	public String getInitials() {
		return documentStatus.getInitials();
	}

	public int getPercentDone() {
		return documentStatus.getPercentDone();
	}
}
