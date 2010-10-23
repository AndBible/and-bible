/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: PdaLuceneIndexManager.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package org.crosswire.jsword.index.lucene;

import java.io.IOException;
import java.net.URI;

import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.index.IndexStatus;

/** Optimise Lucene index creation
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PdaLuceneIndexManager  {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.index.search.AbstractIndex#generateSearchIndex(org
     * .crosswire.common.progress.Job)
     */
    public void scheduleIndexCreation(final Book book) {
    	log.debug("1");
        book.setIndexStatus(IndexStatus.SCHEDULED);

    	log.debug("2");
        Thread work = new Thread(new Runnable() {
            public void run() {
                IndexStatus finalStatus = IndexStatus.UNDONE;

            	log.debug("3");
                try {
                    URI storage = getStorageArea(book);
                	log.debug("4");

                    PdaLuceneIndexCreator index = new PdaLuceneIndexCreator(book, storage, true);
                    // We were successful if the directory exists.
                    if (NetUtil.getAsFile(storage).exists()) {
                        finalStatus = IndexStatus.DONE;
                        book.deactivate(null);
                        book.activate(null);
//                        INDEXES.put(book, index);
                    }
                } catch (IOException e) {
                	System.out.println("error"+e.getMessage());
                	e.printStackTrace();
                    Reporter.informUser(PdaLuceneIndexManager.this, e);
                } catch (BookException e) {
                	System.out.println("error"+e.getMessage());
                	e.printStackTrace();
                    Reporter.informUser(PdaLuceneIndexManager.this, e);
                } catch (Throwable t) {
                	System.out.println("error"+t.getMessage());
                	t.printStackTrace();
                } finally {
                	System.out.println("finished");
                    book.setIndexStatus(finalStatus);
                }
            }
        });
        work.start();
    }

    /**
     * Determine where an index should be stored
     * 
     * @param book
     *            The book to be indexed
     * @return A URI to store stuff in
     * @throws IOException
     *             If there is a problem in finding where to store stuff
     */
    protected URI getStorageArea(Book book) throws IOException {
        BookMetaData bmd = book.getBookMetaData();
        String driverName = bmd.getDriverName();
        String bookName = bmd.getInitials();

        assert driverName != null;
        assert bookName != null;

        URI base = CWProject.instance().getWriteableProjectSubdir(DIR_LUCENE, false);
        URI driver = NetUtil.lengthenURI(base, driverName);

        return NetUtil.lengthenURI(driver, bookName);
    }

    /**
     * The lucene search index directory
     */
    private static final String DIR_LUCENE = "lucene"; //$NON-NLS-1$

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(PdaLuceneIndexManager.class);
}
