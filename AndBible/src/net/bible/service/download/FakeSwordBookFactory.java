package net.bible.service.download;

import java.io.IOException;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookDriver;
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
	public static Book createFakeRepoBook(String module, String conf, String repo) throws IOException {
		SwordBookMetaData sbmd = createRepoSBMD(module, conf);
		sbmd.putProperty(DownloadManager.REPOSITORY_KEY, repo);
		Book extraBook = new SwordBook(sbmd, null);
		return extraBook;
	}

	/** create sbmd for file available for download from repo
	 */
	public static SwordBookMetaData createRepoSBMD(String module, String conf) throws IOException {
		SwordBookMetaData sbmd = new SwordBookMetaData(conf.getBytes(), module);
		BookDriver fake = SwordBookDriver.instance();
		sbmd.setDriver(fake);
		return sbmd;
	}
}
