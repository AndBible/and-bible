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
 * ID: $Id: Installer.java 2050 2010-12-09 15:31:45Z dmsmith $
 */
package org.crosswire.jsword.book.install;

import java.net.URI;
import java.util.List;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookList;

/**
 * An interface that allows us to download from a specific source of Bible data.
 * It is important that implementor of this interface define equals() and
 * hashcode() properly.
 * 
 * <p>
 * To start with I only envisage that we use Sword sourced Bible data however
 * the rest of the system is designed to be able to use data from e-Sword, OLB,
 * etc.
 * </p>
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public interface Installer extends BookList {
    //MJD START
    /** remove the cached book list to clear memory
     */
    void close();
    //MJD END
    
    /**
     * Get the type of the Installer.
     * 
     * @return the type of the installer
     */
    String getType();

    /**
     * Accessor for the URI
     * 
     * @return the source URI
     */
    String getInstallerDefinition();

    /**
     * @param book
     *            The book meta-data to get a URI from.
     * @return the remote URI for the BookMetaData
     */
    URI toRemoteURI(Book book);

    /**
     * Get a list of BookMetaData objects that represent downloadable books. If
     * no list has been retrieved from the remote source using reloadIndex()
     * then we should just return an empty list and not attempt to contact the
     * remote source. See notes on reload for more information.
     * 
     * @see Installer#reloadBookList()
     */
    List<Book> getBooks();

    /**
     * Get a Book matching the name from the local cache. Null if none is found.
     */
    Book getBook(String Book);

    /**
     * Return true if the book is not installed or there is a newer version to
     * install.
     * 
     * @param book
     *            The book meta-data to check on.
     * @return whether there is a newer version to install
     */
    int getSize(Book book);

    /**
     * Return true if the book is not installed or there is a newer version to
     * install.
     * 
     * @param book
     *            The book meta-data to check on.
     * @return whether there is a newer version to install
     */
    boolean isNewer(Book book);

    /**
     * Re-fetch a list of names from the remote source. <b>It would make sense
     * if the user was warned about the implications of this action. If the user
     * lives in a country that persecutes Christians then this action might give
     * the game away.</b>
     */
    void reloadBookList() throws InstallException;

    /**
     * Download and install a book locally. The name should be one from an index
     * list retrieved from getIndex() or reloadIndex()
     * 
     * @param book
     *            The book to install
     */
    void install(Book book) throws InstallException;

    /**
     * Download a search index for the given Book. The installation of the
     * search index is the responsibility of the BookIndexer.
     * 
     * @param book
     *            The book to download a search index for.
     * @param tempDest
     *            A temporary URI for downloading to. Passed to the BookIndexer
     *            for installation.
     */
    void downloadSearchIndex(Book book, URI tempDest) throws InstallException;
}
