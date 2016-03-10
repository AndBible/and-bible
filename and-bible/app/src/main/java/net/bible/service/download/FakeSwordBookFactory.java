package net.bible.service.download;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.BookDriver;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.sword.NullBackend;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.book.sword.SwordBookMetaData;

/** Create dummy sword Books used to download from Xiphos Repo that has unusual download file case
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class FakeSwordBookFactory {

	/** create dummy Book object for file available for download from repo
	 */
	public static SwordBook createFakeRepoBook(String module, String conf, String repo) throws IOException, BookException {
		SwordBookMetaData sbmd = createRepoSBMD(module, conf);
		if (StringUtils.isNotEmpty(repo)) {
			sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repo);
		}
		SwordBook extraBook = new SwordBook(sbmd, new NullBackend());
		return extraBook;
	}

	/** create sbmd for file available for download from repo
	 */
	public static SwordBookMetaData createRepoSBMD(String module, String conf) throws IOException, BookException {
		SwordBookMetaData sbmd = new SwordBookMetaData(conf.getBytes(), module);
		BookDriver fake = SwordBookDriver.instance();
		sbmd.setDriver(fake);
		return sbmd;
	}
}
