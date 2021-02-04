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
package net.bible.service.download

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.IOException

/** Create dummy sword Books used to download from Xiphos Repo that has unusual download file case
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object FakeBookFactory {
    /** create dummy Book object for file available for download from repo
     */
    @JvmStatic
	@Throws(IOException::class, BookException::class)
    fun createFakeRepoSwordBook(module: String?, conf: String, repo: String?): SwordBook {
        val sbmd = createRepoSBMD(module, conf)
        if (StringUtils.isNotEmpty(repo)) {
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repo)
        }
        return SwordBook(sbmd, NullBackend())
    }

    /** create sbmd for file available for download from repo
     */
    @Throws(IOException::class, BookException::class)
    fun createRepoSBMD(module: String?, conf: String): SwordBookMetaData {
        val sbmd = SwordBookMetaData(conf.toByteArray(), module)
        val fake = SwordBookDriver.instance()
        sbmd.driver = fake
        return sbmd
    }
}
