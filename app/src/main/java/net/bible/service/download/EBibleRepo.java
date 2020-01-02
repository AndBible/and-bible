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

import java.util.List;

import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

/** some books need renaming after download due to problems with Xiphos module case
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class EBibleRepo extends RepoBase {

	private static final String REPOSITORY = "eBible";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();
	
	@SuppressWarnings("unused")
	private static final String TAG = "EBibleRepo";
	
	
	/** get a list of books that are available in AndBible repo
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		
        List<Book> bookList = getBookList(SUPPORTED_DOCUMENTS, refresh);

        storeRepoNameInMetaData(bookList);
        
		return bookList;
	}

	@Override
	public String getRepoName() {
		return REPOSITORY;
	}
}
