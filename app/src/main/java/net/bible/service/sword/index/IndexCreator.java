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
package net.bible.service.sword.index;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;

/** Optimise Lucene index creation
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class IndexCreator  {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.index.search.AbstractIndex#generateSearchIndex(org
     * .crosswire.common.progress.Job)
     */
    public void scheduleIndexCreation(final Book book) {
        Thread work = new Thread(new Runnable() {
            public void run() {
            	IndexManager indexManager = IndexManagerFactory.getIndexManager();
            	indexManager.setIndexPolicy(new AndroidIndexPolicy());
                indexManager.scheduleIndexCreation(book);
            }
        });
        work.start();
    }
}
