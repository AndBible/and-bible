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

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.Logger;

import org.crosswire.common.util.Filter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.install.InstallManager;
import org.crosswire.jsword.book.install.Installer;
import org.crosswire.jsword.book.sword.SwordBookMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Originally copied from BookInstaller it calls Sword routines related to installation and removal of books and indexes
 * 
 * @author DM Smith [dmsmith555 at yahoo dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DownloadManager {

	public static final String REPOSITORY_KEY = "repository";

	private InstallManager installManager;

    private static final Logger log = new Logger(DownloadManager.class.getName());
    
    public DownloadManager() {
        installManager = new InstallManager();
    }

	/**
	 * @param filter
	 * @return
	 */
	public List<Book> getDownloadableBooks(BookFilter filter, String repo, boolean refresh) throws InstallException {

		List<Book> documents = new ArrayList<>();
		
		Installer installer = null;
		try {
	        // If we know the name of the installer we can get it directly
	        installer = installManager.getInstaller(repo);
	        
	        if (installer==null) {
				log.error("Error getting installer for repo "+repo);
				Dialogs.getInstance().showErrorMsg(R.string.error_occurred, new Exception("Error getting installer for repo "+repo));
				documents = Collections.emptyList();
	        } else {
		        // Now we can get the list of books
		    	log.debug("getting downloadable books");
		    	if (refresh || installer.getBooks().size()==0) {
		    		//todo should warn user of implications of downloading book list e.g. from persecuted country
		    		log.warn("Reloading book list");
		    		installer.reloadBookList();
		    	}
		
		        // Get a list of all the available books
		        documents = installer.getBooks(filter); //$NON-NLS-1$
	        }
	
		} catch (Exception e) {
			// ignore error because some minor repos are unreliable
			log.error("Fatal error downloading books from "+repo, e);
		} catch (OutOfMemoryError oom) {
			// eBible repo throws OOM errors on smaller devices
			log.error("Out of memory error downloading books from "+repo);
		} finally {
	        //free memory
	        if (installer!=null) {
	        	installer.close();
	        }
	        System.gc();
		}
    	log.info("number of documents available:"+documents.size());
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
    public void installBook(final String repositoryName, final Book book) throws BookException, InstallException {
        // Delete the book, if present
        // At the moment, JSword will not re-install. Later it will, if the
        // remote version is greater.
        String bookInitials = book.getInitials();
        Book installedBook = Books.installed().getBook(bookInitials);
        if (installedBook!= null) {
        	// installedBook may differ by case of initials if Xiphos so delete installedBook rather than book
            unregisterBook(installedBook);
        }

		try {
			// An installer knows how to install books
			Installer installer = installManager.getInstaller(repositoryName);

			installer.install(book);

			// reload metadata to ensure the correct location is set, otherwise maps won't show
			((SwordBookMetaData)book.getBookMetaData()).reload(new Filter() {
				@Override
				public boolean test(Object obj) {
					return true;
				}
			});

		} catch (InstallException ex) {
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, ex);
		}
	}

    /**
     * Install a book's index, overwriting it if it exists
     * 
     * @param repositoryName
     *            the name of the repository from which to get the index
     * @param book
     *            the book to get the index for
     * @throws BookException
     * @throws InstallException
     */
    public void installIndexInNewThread(String repositoryName, Book book) throws BookException, InstallException {
    	// An installer knows how to install indexes
        log.debug("Install index in new thread");
        Installer installer = installManager.getInstaller(repositoryName);
    	IndexDownloader idt = new IndexDownloader();
    	idt.downloadIndexInNewThread(installer, book);
    }

	/**
	 * Install a book's index, overwriting it if it exists
	 *
	 * @param repositoryName
	 *            the name of the repository from which to get the index
	 * @param book
	 *            the book to get the index for
	 * @throws BookException
	 * @throws InstallException
	 */
	public void installIndex(String repositoryName, Book book) throws BookException, InstallException {
		// An installer knows how to install indexes
		log.debug("Install index for "+book.getInitials());
		Installer installer = installManager.getInstaller(repositoryName);
		IndexDownloader idt = new IndexDownloader();
		idt.downloadIndex(installer, book);
	}

    /**
     * Unregister a book from Sword registry.
     * 
     * This used to delete the book but there is an mysterious bug in deletion (see below). 
     * 
     * @param book
     *            the book to delete
     * @throws BookException
     */
    private void unregisterBook(Book book) throws BookException {
    	// this just seems to work so leave it here
    	// I used to think that the next delete was better - what a mess
    	// see this for potential problem: http://stackoverflow.com/questions/20437626/file-exists-returns-false-for-existing-file-in-android
    	// does file.exists return an incorrect value?
    	// To see the problem, reverse the commented lines below, and try downloading 2 or more Bibles that are already installed
        Books.installed().removeBook(book);

        // Avoid deleting all dir and files because "Java is known not to delete files immediately, so mkdir may fail sometimes"
        // http://stackoverflow.com/questions/617414/create-a-temporary-directory-in-java
        //
        // Actually do the delete
        // This should be a call on installer.
        //book.getDriver().delete(book);
    }

    /**
     * Get a list of all known installers.
     * 
     * @return the list of installers
     */
    public Map<String, Installer> getInstallers() {
        // Ask the Install Manager for a map of all known remote repositories
        // sites
        return installManager.getInstallers();
    }

    /**
     * Get a list of books in a repository by BookFilter.
     * 
     * @param filter
     *            The book filter
     * @see BookFilter
     * @see Books
     */
    public List<Book> getRepositoryBooks(String repositoryName, BookFilter filter) {
        return installManager.getInstaller(repositoryName).getBooks(filter);
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
     * @param bookInitials
     *            the name of the book to get
     * @return the Book
     */
    public Book getRepositoryBook(String repositoryName, String bookInitials) {
        return installManager.getInstaller(repositoryName).getBook(bookInitials);
    }
}
