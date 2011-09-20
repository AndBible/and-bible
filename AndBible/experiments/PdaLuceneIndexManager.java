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
 * ID: $Id: LuceneIndexManager.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package net.bible.service.sword;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.index.Index;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.LuceneIndexManager;

/**
 * An implementation of IndexManager for Lucene indexes.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class PdaLuceneIndexManager extends LuceneIndexManager{

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
                IndexStatus finalStatus = IndexStatus.UNDONE;

                try {
                    URI storage = getStorageArea(book);
                    Index index = new PdaLuceneIndexCreator(book, storage, true);
                    // We were successful if the directory exists.
                    if (NetUtil.getAsFile(storage).exists()) {
                        finalStatus = IndexStatus.DONE;
                        INDEXES.put(book, index);
                    }
                } catch (IOException e) {
                    Reporter.informUser(PdaLuceneIndexManager.this, e);
                } catch (BookException e) {
                    Reporter.informUser(PdaLuceneIndexManager.this, e);
                } finally {
                    book.setIndexStatus(finalStatus);
                }
            }
        });
        work.start();
    }

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(PdaLuceneIndexManager.class);
}
