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

import net.bible.service.common.Logger;
import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

import java.util.List;

/** AndBible repo is mainly used to override modules with temporary issues.  It also contains a few tweaked modules from the Xiphos repo.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class AndBibleRepo extends RepoBase {

	private static final String REPOSITORY = "AndBible";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();

	private Logger log = new Logger(this.getClass().getName());

	/** get a list of books that are available in AndBible repo
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		
        List<Book> bookList = getBookList(SUPPORTED_DOCUMENTS, refresh);

        storeRepoNameInMetaData(bookList);
        
		return bookList;		
	}

	/**
	 * Download the index of the specified document
	 */
	public void downloadIndex(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installIndex(getRepoName(), document);
	}

	@Override
	public String getRepoName() {
		return REPOSITORY;
	}
}
