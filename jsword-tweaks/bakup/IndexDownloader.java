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
 * ID: $Id: IndexDownloader.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package org.crosswire.jsword.util;

import java.io.IOException;
import java.net.URI;

import org.crosswire.common.util.NetUtil;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.install.Installer;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;
import org.crosswire.jsword.index.IndexStatus;

/**
 * .
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public final class IndexDownloader {
    /**
     * Prevent instantiation
     */
    private IndexDownloader() {
    }

    /**
     * Download and install a search index
     * 
     * @param book
     *            The book to get an index for
     */
    public static void downloadIndex(Book book, Installer installer) throws IOException, InstallException, BookException {
        // Get a temp home
        URI tempDownload = NetUtil.getTemporaryURI(TEMP_PREFIX, TEMP_SUFFIX);

        try {
            // Now we know what installer to use, download to the temp file
            installer.downloadSearchIndex(book, tempDownload);

            // And install from that file.
            IndexManager idxman = IndexManagerFactory.getIndexManager();
            idxman.installDownloadedIndex(book, tempDownload);
            
            //MJD START activate the index
            // the index is now downloaded so ensure the Sword Book is updated
            book.deactivate(null);
            book.activate(null);
            book.setIndexStatus(IndexStatus.DONE);
            //MJD END
        } finally {
            // tidy up after ourselves
            if (tempDownload != null) {
                NetUtil.delete(tempDownload);
            }
        }
    }

    /**
     * Temp file prefix
     */
    private static final String TEMP_PREFIX = "jsword-index"; //$NON-NLS-1$

    /**
     * Temp file suffix
     */
    private static final String TEMP_SUFFIX = "dat"; //$NON-NLS-1$
}
