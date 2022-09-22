/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.navigation;

import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
//@Ignore("Until ESV comes back")
public class DocumentBibleBooksFactoryTest {

	private DocumentBibleBooksFactory documentBibleBooksFactory;
	private AbstractPassageBook esv;

	@Before
	public void setup() {
		documentBibleBooksFactory = new DocumentBibleBooksFactory();
		esv = (AbstractPassageBook) Books.installed().getBook("ESV2011");
	}

	@After
	public void tearDown() {
		// ensure it is in the list after removal by some tests
		Books.installed().addBook(esv);
	}

	@Test
	public void initialise_shouldInstallBookChangeListenersToResetCache() throws Exception {
		documentBibleBooksFactory.initialise();
		assertThat(documentBibleBooksFactory.size(), equalTo(0));

		documentBibleBooksFactory.getBooksFor(esv);
		assertThat(documentBibleBooksFactory.size(), equalTo(1));

		Books.installed().removeBook(esv);
		assertThat(documentBibleBooksFactory.size(), equalTo(0));
	}

	@Test
	public void getDocumentBibleBooksFor() throws Exception {
		final List<BibleBook> esvBibleBooks = documentBibleBooksFactory.getBooksFor(esv);
		assertThat(documentBibleBooksFactory.size(), equalTo(1));
		assertThat(true, is(esvBibleBooks.contains(BibleBook.GEN)));
	}

	@Test
	public void getBooksFor() throws Exception {
		final List<BibleBook> esvBibleBooks = documentBibleBooksFactory.getBooksFor(esv);
		assertThat(true, is(esvBibleBooks.contains(BibleBook.GEN)));
	}
}
