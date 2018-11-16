package net.bible.android.control.download;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class DocumentStatus {

	public enum DocumentInstallStatus {INSTALLED, NOT_INSTALLED, BEING_INSTALLED, UPGRADE_AVAILABLE, ERROR_DOWNLOADING}

	private final String initials;

	private final DocumentInstallStatus documentInstallStatus;

	private final int percentDownloaded;

	public DocumentStatus(String initials, DocumentInstallStatus documentInstallStatus, int percentDownloaded) {
		this.initials = initials;
		this.documentInstallStatus = documentInstallStatus;
		this.percentDownloaded = percentDownloaded;
	}

	public String getInitials() {
		return initials;
	}

	public DocumentInstallStatus getDocumentInstallStatus() {
		return documentInstallStatus;
	}

	public int getPercentDone() {
		return percentDownloaded;
	}
}
