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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.FileUtil;
import org.crosswire.common.util.IOUtil;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.index.Index;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexStatus;

/**
 * An implementation of IndexManager for Lucene indexes.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class PdaLuceneIndexManager implements IndexManager {
    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.index.search.AbstractIndex#isIndexed()
     */
    public boolean isIndexed(Book book) {
        try {
            URI storage = getStorageArea(book);
            return NetUtil.isDirectory(storage);
        } catch (IOException ex) {
            log.error("Failed to find lucene index storage area.", ex); //$NON-NLS-1$
            return false;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.index.search.IndexManager#getIndex(org.crosswire
     * .jsword.book.Book)
     */
    public Index getIndex(Book book) throws BookException {
        try {
            Index reply = (Index) INDEXES.get(book);
            if (reply == null) {
                URI storage = getStorageArea(book);
                reply = new PdaLuceneIndex(book, storage);
                INDEXES.put(book, reply);
            }

            return reply;
        } catch (IOException ex) {
            throw new BookException(UserMsg.LUCENE_INIT, ex);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.index.search.AbstractIndex#generateSearchIndex(org
     * .crosswire.common.progress.Job)
     */
    public void scheduleIndexCreation(final Book book) {
        book.setIndexStatus(IndexStatus.SCHEDULED);

        Thread work = new Thread(new Runnable() {
            public void run() {
            	System.out.println("*** running");
                IndexStatus finalStatus = IndexStatus.UNDONE;
            	System.out.println("*** 1");

                try {
                    URI storage = getStorageArea(book);
                	System.out.println("*** 2");
                    Index index = new PdaLuceneIndex(book, storage, true);
                	System.out.println("*** 3");
                    // We were successful if the directory exists.
                    if (NetUtil.getAsFile(storage).exists()) {
                    	System.out.println("*** 4");
                        finalStatus = IndexStatus.DONE;
                        INDEXES.put(book, index);
                    }
                	System.out.println("*** 5");
                } catch (IOException e) {
                	System.out.println("*** error"+e.getMessage());
                	e.printStackTrace();
                    Reporter.informUser(PdaLuceneIndexManager.this, e);
                } catch (BookException e) {
                	System.out.println("*** error"+e.getMessage());
                	e.printStackTrace();
                    Reporter.informUser(PdaLuceneIndexManager.this, e);
                } catch (Throwable t) {
                	System.out.println("*** error"+t.getMessage());
                	t.printStackTrace();
                } finally {
                	System.out.println("*** finished");
                    book.setIndexStatus(finalStatus);
                }
            }
        });
        work.start();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.index.search.IndexManager#installDownloadedIndex
     * (org.crosswire.jsword.book.Book, java.net.URI)
     */
    public void installDownloadedIndex(Book book, URI tempDest) throws BookException {
        try {
            URI storage = getStorageArea(book);
            File zip = NetUtil.getAsFile(tempDest);
            IOUtil.unpackZip(zip, NetUtil.getAsFile(storage));
        } catch (IOException ex) {
            throw new BookException(UserMsg.INSTALL_FAIL, ex);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.index.search.IndexManager#deleteIndex(org.crosswire
     * .jsword.book.Book)
     */
    public void deleteIndex(Book book) throws BookException {
        // Lucene can build in the directory that currently exists,
        // overwriting what is there. So we rename the directory,
        // mark the operation as success and then try to delete the
        // directory.
        File tempPath = null;
        try {
            // TODO(joe): This needs some checks that it isn't being used
            File storage = NetUtil.getAsFile(getStorageArea(book));
            String finalCanonicalPath = storage.getCanonicalPath();
            tempPath = new File(finalCanonicalPath + '.' + IndexStatus.CREATING.toString());
            FileUtil.delete(tempPath);
            if (!storage.renameTo(tempPath)) {
                throw new BookException(UserMsg.DELETE_FAILED);
            }
            book.setIndexStatus(IndexStatus.UNDONE);
        } catch (IOException ex) {
            throw new BookException(UserMsg.DELETE_FAILED, ex);
        }

        FileUtil.delete(tempPath);
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
     * The created indexes
     */
    protected static final Map INDEXES = new HashMap();

    /**
     * The lucene search index directory
     */
    private static final String DIR_LUCENE = "lucene"; //$NON-NLS-1$

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(PdaLuceneIndexManager.class);
}
