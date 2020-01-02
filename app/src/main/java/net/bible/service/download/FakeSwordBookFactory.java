/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.download;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.BookDriver;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.sword.NullBackend;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.book.sword.SwordBookMetaData;

import java.io.IOException;

/** Create dummy sword Books used to download from Xiphos Repo that has unusual download file case
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
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
