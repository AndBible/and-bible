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
 * Copyright: 2008
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: BookIndexer.java 1466 2007-07-02 02:48:09Z dmsmith $
 */
package net.bible.service.download;

import java.util.List;
import java.util.Map;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.install.InstallManager;
import org.crosswire.jsword.book.install.Installer;

import android.util.Log;

/**
 * Originally copied from BookInstaller it calls Sword routines related to installation and removal of books and indexes
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith [dmsmith555 at yahoo dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DownloadManager {

	private static final String TAG = "DownloadManager";
	
    public DownloadManager() {
        installManager = new InstallManager();
    }

	/**
	 * @param filter
	 * @return
	 */
	public List<Book> getDownloadableBooks(BookFilter filter, String repo) throws InstallException {

        // If we know the name of the installer we can get it directly
        Installer installer = installManager.getInstaller(repo);

        // Now we can get the list of books
    	Log.d(TAG, "getting downloadable books");
    	if (installer.getBooks().size()==0) {
    		//todo should warn user of implications of downloading book list e.g. from persecuted country
    		Log.w(TAG, "Auto reloading book list");
    		installer.reloadBookList();
    	}

        // Get a list of all the available books
        List<Book> documents = installer.getBooks(filter); //$NON-NLS-1$
    	Log.i(TAG, "number of documents available:"+documents.size());

		return documents;
	}
	
    /**
     * Install a book, overwriting it if the book to be installed is newer.
     * 
     * @param repositoryName
     *            the name of the repository from which to get the book
     * @param book
     *            the book to get
     * @throws BookException
     * @throws InstallException
     */
    public void installBook(String repositoryName, Book book) throws BookException, InstallException {
        // An installer knows how to install books
        Installer installer = installManager.getInstaller(repositoryName);

        // Delete the book, if present
        // At the moment, JSword will not re-install. Later it will, if the
        // remote version is greater.
        if (Books.installed().getBook(book.getInitials()) != null) {
            deleteBook(book);
        }

        // Now install it. Note this is a background task.
        installer.install(book);
    }

    /**
     * Install a book, overwriting it if the book to be installed is newer.
     * 
     * @param repositoryName
     *            the name of the repository from which to get the book
     * @param book
     *            the book to get
     * @throws BookException
     * @throws InstallException
     */
    public void installIndex(String repositoryName, Book book) throws BookException, InstallException {
    	// An installer knows how to install indexes
        Installer installer = installManager.getInstaller(repositoryName);
        Log.d(TAG, "** getting index 1");

// moved to experimental section
//    	IndexDownloadThread idt = new IndexDownloadThread();
//    	idt.downloadIndex(installer, book);
    }

    /**
     * Uninstall a book.
     * 
     * @param book
     *            the book to delete
     * @throws BookException
     */
    public void deleteBook(Book book) throws BookException {
        // Make the book unavailable.
        // This is normally done via listeners.
        Books.installed().removeBook(book);

        // Actually do the delete
        // This should be a call on installer.
        book.getDriver().delete(book);
    }

    /**
     * Get a list of all known installers.
     * 
     * @return the list of installers
     */
    public Map getInstallers() {
        // Ask the Install Manager for a map of all known remote repositories
        // sites
        return installManager.getInstallers();
    }

    /**
     * Get a list of all installed books.
     * 
     * @return the list of installed books
     */
    public static List getInstalledBooks() {
        return Books.installed().getBooks();
    }

    /**
     * Get a list of installed books by BookFilter.
     * 
     * @param filter
     *            The book filter
     * @see BookFilter
     * @see Books
     */
    public static List getInstalledBooks(BookFilter filter) {
        return Books.installed().getBooks(filter);
    }

    /**
     * Get a list of books by CustomFilter specification
     * 
     * @param filterSpec
     *            The filter string
     * @see BookFilters#getCustom(java.lang.String)
     * @see Books
     */
    public static List getInstalledBooks(String filterSpec) {
        return getInstalledBooks(BookFilters.getCustom(filterSpec));
    }

    /**
     * Get a particular installed book by initials.
     * 
     * @param bookInitials
     *            The book name to search for
     * @return The found book. Null otherwise.
     */
    public static Book getInstalledBook(String bookInitials) {
        return Books.installed().getBook(bookInitials);
    }

    /**
     * Get a list of all known books for an installer.
     * 
     * @param repositoryName
     * @return the list of books at that repository
     */
    public List getRepositoryBooks(String repositoryName) {
        return installManager.getInstaller(repositoryName).getBooks();
    }

    /**
     * Get a list of books in a repository by BookFilter.
     * 
     * @param filter
     *            The book filter
     * @see BookFilter
     * @see Books
     */
    public List getRepositoryBooks(String repositoryName, BookFilter filter) {
        System.out.println("Install manager:"+installManager);
        Map installers = installManager.getInstallers();
        for (Object installer : installers.values()) {
            System.out.println(installer);
        }
        return installManager.getInstaller(repositoryName).getBooks(filter);
    }

    /**
     * Get a list of books in a repository by CustomFilter specification
     * 
     * @param filterSpec
     *            The filter string
     * @see BookFilters#getCustom(java.lang.String)
     * @see Books
     */
    public List getRepositoryBooks(String repositoryName, String filterSpec) {
        return getRepositoryBooks(repositoryName, BookFilters.getCustom(filterSpec));
    }

    /**
     * Get a particular installed book by initials.
     * 
     * @param bookInitials
     *            The book name to search for
     * @return The found book. Null otherwise.
     */
    public Book getRepositoryBook(String repositoryName, String bookInitials) {
        return installManager.getInstaller(repositoryName).getBook(bookInitials);
    }

    /**
     * Reload the local cache for a remote repository.
     * 
     * @param repositoryName
     * @throws InstallException
     */
    public void reloadBookList(String repositoryName) throws InstallException {
        installManager.getInstaller(repositoryName).reloadBookList();
    }

    /**
     * Get a Book from the repository. Note this does not install it.
     * 
     * @param repositoryName
     *            the repository from which to get the book
     * @param bookName
     *            the name of the book to get
     * @return the Book
     */
    public Book getBook(String repositoryName, String bookName) {
        return installManager.getInstaller(repositoryName).getBook(bookName);
    }


    private InstallManager installManager;
}
