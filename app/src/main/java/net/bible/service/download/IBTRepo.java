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

/** Modules from the Institute of Bible Translation
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class IBTRepo extends RepoBase {

	private static final String REPOSITORY = "IBT";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();
	
	/** get a list of books that are available in default repo and seem to work in And Bible
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {

		List<Book> books = getBookList(SUPPORTED_DOCUMENTS, refresh);
		storeRepoNameInMetaData(books);
		
		return books;
	}
	
	@Override
	public String getRepoName() {
		return REPOSITORY;
	}
}
