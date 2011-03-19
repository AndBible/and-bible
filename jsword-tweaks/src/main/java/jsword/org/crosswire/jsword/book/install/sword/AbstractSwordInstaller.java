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
 * ID: $Id: AbstractSwordInstaller.java 2054 2010-12-10 22:12:09Z dmsmith $
 */
package org.crosswire.jsword.book.install.sword;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.CollectionUtil;
import org.crosswire.common.util.IOUtil;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookDriver;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilterIterator;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.BookSet;
import org.crosswire.jsword.book.basic.AbstractBookList;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.install.Installer;
import org.crosswire.jsword.book.sword.ConfigEntry;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.book.sword.SwordBookMetaData;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.book.sword.SwordConstants;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

/**
 * .
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public abstract class AbstractSwordInstaller extends AbstractBookList implements Installer, Comparable<AbstractSwordInstaller> {
    /**
     * Utility to download a file from a remote site
     * 
     * @param job
     *            The way of noting progress
     * @param dir
     *            The directory from which to download the file
     * @param file
     *            The file to download
     * @throws InstallException
     */
    protected abstract void download(Progress job, String dir, String file, URI dest) throws InstallException;

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.install.Installer#getInstallerDefinition()
     */
    public String getInstallerDefinition() {
        StringBuilder buf = new StringBuilder(host);
        buf.append(',');
        buf.append(packageDirectory);
        buf.append(',');
        buf.append(catalogDirectory);
        buf.append(',');
        buf.append(indexDirectory);
        buf.append(',');
        if (proxyHost != null) {
            buf.append(proxyHost);
        }
        buf.append(',');
        if (proxyPort != null) {
            buf.append(proxyPort);
        }
        return buf.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.install.Installer#isNewer(org.crosswire.jsword
     * .book.BookMetaData)
     */
    public boolean isNewer(Book book) {
        File dldir = SwordBookPath.getSwordDownloadDir();

        SwordBookMetaData sbmd = (SwordBookMetaData) book.getBookMetaData();
        File conf = new File(dldir, sbmd.getConfPath());

        // The conf may not exist in our download dir.
        // In this case we say that it should not be downloaded again.
        if (!conf.exists()) {
            return false;
        }

        URI configURI = NetUtil.getURI(conf);

        URI remote = toRemoteURI(book);
        return NetUtil.isNewer(remote, configURI, proxyHost, proxyPort);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.BookList#getBooks()
     */
    public List<Book> getBooks() {
        try {
            if (!loaded) {
                loadCachedIndex();
            }

            // We need to create a List from the Set returned by
            // entries.values() so the underlying list is not modified.
            return new ArrayList<Book>(entries.values());
        } catch (InstallException ex) {
            log.error("Failed to reload cached index file", ex);
            return new ArrayList<Book>();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.BookList#getBook(java.lang.String)
     */
    public synchronized Book getBook(String name) {
        // Check name first
        // First check for exact matches
        for (Book book : getBooks()) {
            if (name.equals(book.getName())) {
                return book;
            }
        }

        // Next check for case-insensitive matches
        for (Book book : getBooks()) {
            if (name.equalsIgnoreCase(book.getName())) {
                return book;
            }
        }

        // Then check initials
        // First check for exact matches
        for (Book book : getBooks()) {
            BookMetaData bmd = book.getBookMetaData();
            if (name.equals(bmd.getInitials())) {
                return book;
            }
        }

        // Next check for case-insensitive matches
        for (Book book : getBooks()) {
            if (name.equalsIgnoreCase(book.getInitials())) {
                return book;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.BookList#getBooks(org.crosswire.jsword.book
     * .BookFilter)
     */
    @Override
    public synchronized List<Book> getBooks(BookFilter filter) {
        List<Book> temp = CollectionUtil.createList(new BookFilterIterator(getBooks(), filter));
        return new BookSet(temp);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.install.Installer#install(org.crosswire.jsword
     * .book.Book)
     */
    public void install(Book book) {
        // // Is the book already installed? Then nothing to do.
        // if (Books.installed().getBook(book.getName()) != null)
        // {
        // return;
        // }
        //
        final SwordBookMetaData sbmd = (SwordBookMetaData) book.getBookMetaData();

        // So now we know what we want to install - all we need to do
        // is installer.install(name) however we are doing it in the
        // background so we create a job for it.
        final Thread worker = new Thread("DisplayPreLoader")
        {
            /*
             * (non-Javadoc)
             * 
             * @see java.lang.Runnable#run()
             */
            @Override
            public void run() {
                // TRANSLATOR: Progress label indicating the installation of a book. {0} is a placeholder for the name of the book.
                String jobName = UserMsg.gettext("Installing book: {0}", sbmd.getName());
                Progress job = JobManager.createJob(jobName, this);

                // Don't bother setting a size, we'll do it later.
                job.beginJob(jobName);

                yield();

                URI temp = null;
                try {
                    // TRANSLATOR: Progress label indicating the Initialization of installing of a book.
                    job.setSectionName(UserMsg.gettext("Initializing"));

                    temp = NetUtil.getTemporaryURI("swd", ZIP_SUFFIX);

                    download(job, packageDirectory, sbmd.getInitials() + ZIP_SUFFIX, temp);

                    // Once the unzipping is started, we need to continue
                    job.setCancelable(false);
                    if (!job.isFinished()) {
                        File dldir = SwordBookPath.getSwordDownloadDir();
                        IOUtil.unpackZip(NetUtil.getAsFile(temp), dldir);
                        // TRANSLATOR: Progress label for installing the conf file for a book.
                        job.setSectionName(UserMsg.gettext("Copying config file"));
                        sbmd.setLibrary(NetUtil.getURI(dldir));
                        SwordBookDriver.registerNewBook(sbmd);
                    }

                } catch (IOException e) {
                    Reporter.informUser(this, e);
                    job.cancel();
                } catch (InstallException e) {
                    Reporter.informUser(this, e);
                    job.cancel();
                } catch (BookException e) {
                    Reporter.informUser(this, e);
                    job.cancel();
                } finally {
                    job.done();
                    // tidy up after ourselves
                    // This is a best effort. If for some reason it does not delete now
                    // it will automatically be deleted when the JVM exits normally.
                    if (temp != null) {
                        try {
                            NetUtil.delete(temp);
                        } catch (IOException e) {
                            log.warn("Error deleting temp download file:" + e.getMessage());
                        }
                    }
                }
            }
        };

        // this actually starts the thread off
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.install.Installer#reloadIndex()
     */
    public void reloadBookList() throws InstallException {
        // TRANSLATOR: Progress label for downloading one or more files.
        String jobName = UserMsg.gettext("Downloading files");
        Progress job = JobManager.createJob(jobName, Thread.currentThread());
        job.beginJob(jobName);

        try {
            URI scratchfile = getCachedIndexFile();
            download(job, catalogDirectory, FILE_LIST_GZ, scratchfile);
            loaded = false;
        } catch (InstallException ex) {
            job.cancel();
            throw ex;
        } finally {
            job.done();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.install.Installer#downloadSearchIndex(org.crosswire
     * .jsword.book.BookMetaData, java.net.URI)
     */
    public void downloadSearchIndex(Book book, URI localDest) throws InstallException {
        // TRANSLATOR: Progress label for downloading one or more files.
        String jobName = UserMsg.gettext("Downloading files");
        Progress job = JobManager.createJob(jobName, Thread.currentThread());
        job.beginJob(jobName);

		//MJD START
        //use and-bible index location
        String indexLocation = "/and-bible/indices/v1";
        try {
            String version = (String)book.getBookMetaData().getProperty("Version");
            String versionSuffix = version!=null ? "-"+version : "";
            download(job, indexLocation, book.getInitials()+versionSuffix + ZIP_SUFFIX, localDest);
		//MJD END
//          download(job, packageDirectory + '/' + SEARCH_DIR, book.getInitials() + ZIP_SUFFIX, localDest);
        } catch (InstallException ex) {
            job.cancel();
            throw ex;
        } finally {
            job.done();
        }
    }

    /**
     * Load the cached index file into memory
     */
    private void loadCachedIndex() throws InstallException {
        // We need a sword book driver so the installer can use the driver
        // name to use in deciding where to put the index.
        BookDriver fake = SwordBookDriver.instance();

        entries.clear();

        URI cache = getCachedIndexFile();
        if (!NetUtil.isFile(cache)) {
            reloadBookList();
        }

        InputStream in = null;
        GZIPInputStream gin = null;
        TarInputStream tin = null;
        try {
            ConfigEntry.resetStatistics();

            in = NetUtil.getInputStream(cache);
            gin = new GZIPInputStream(in);
            tin = new TarInputStream(gin);
            while (true) {
                TarEntry entry = tin.getNextEntry();
                if (entry == null) {
                    break;
                }

                String internal = entry.getName();
                if (!entry.isDirectory()) {
                    try {
                        int size = (int) entry.getSize();

                        // Every now and then an empty entry sneaks in
                        if (size == 0) {
                            log.error("Empty entry: " + internal);
                            continue;
                        }

                        byte[] buffer = new byte[size];
                        if (tin.read(buffer) != size) {
                            // This should not happen, but if it does then skip
                            // it.
                            log.error("Did not read all that was expected " + internal);
                            continue;
                        }

                        if (internal.endsWith(SwordConstants.EXTENSION_CONF)) {
                            internal = internal.substring(0, internal.length() - 5);
                        } else {
                            log.error("Not a SWORD config file: " + internal);
                            continue;
                        }

                        if (internal.startsWith(SwordConstants.DIR_CONF + '/')) {
                            internal = internal.substring(7);
                        }

                        SwordBookMetaData sbmd = new SwordBookMetaData(buffer, internal);
                        sbmd.setDriver(fake);
                        Book book = new SwordBook(sbmd, null);
                        entries.put(book.getName(), book);
                    } catch (IOException ex) {
                        log.error("Failed to load config for entry: " + internal, ex);
                    }
                }
            }

            loaded = true;

            ConfigEntry.dumpStatistics();
        } catch (IOException ex) {
            throw new InstallException(Msg.CACHE_ERROR, ex);
        } finally {
            IOUtil.close(tin);
            IOUtil.close(gin);
            IOUtil.close(in);
        }
    }

	//MJD START - allow list to be cleared to clear a large block of memory
    @Override
    public void close() {
        entries.clear();
        loaded = false;
    }
    //MJD END

    /**
     * @return the catologDirectory
     */
    public String getCatalogDirectory() {
        return catalogDirectory;
    }

    /**
     * @param catologDirectory
     *            the catologDirectory to set
     */
    public void setCatalogDirectory(String catologDirectory) {
        this.catalogDirectory = catologDirectory;
    }

    /**
     * @return Returns the directory.
     */
    public String getPackageDirectory() {
        return packageDirectory;
    }

    /**
     * @param newDirectory
     *            The directory to set.
     */
    public void setPackageDirectory(String newDirectory) {
        if (packageDirectory == null || !packageDirectory.equals(newDirectory)) {
            packageDirectory = newDirectory;
            loaded = false;
        }
    }

    /**
     * @return the indexDirectory
     */
    public String getIndexDirectory() {
        return indexDirectory;
    }

    /**
     * @param indexDirectory
     *            the indexDirectory to set
     */
    public void setIndexDirectory(String indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    /**
     * @return Returns the host.
     */
    public String getHost() {
        return host;
    }

    /**
     * @param newHost
     *            The host to set.
     */
    public void setHost(String newHost) {
        if (host == null || !host.equals(newHost)) {
            host = newHost;
            loaded = false;
        }
    }

    /**
     * @return Returns the proxyHost.
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * @param newProxyHost
     *            The proxyHost to set.
     */
    public void setProxyHost(String newProxyHost) {
        String pHost = null;
        if (newProxyHost != null && newProxyHost.length() > 0) {
            pHost = newProxyHost;
        }
        if (proxyHost == null || !proxyHost.equals(pHost)) {
            proxyHost = pHost;
            loaded = false;
        }
    }

    /**
     * @return Returns the proxyPort.
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * @param newProxyPort
     *            The proxyPort to set.
     */
    public void setProxyPort(Integer newProxyPort) {
        if (proxyPort == null || !proxyPort.equals(newProxyPort)) {
            proxyPort = newProxyPort;
            loaded = false;
        }
    }

    /**
     * The URL for the cached index file for this installer
     */
    protected URI getCachedIndexFile() throws InstallException {
        try {
            URI scratchdir = CWProject.instance().getWriteableProjectSubdir(getTempFileExtension(host, catalogDirectory), true);
            return NetUtil.lengthenURI(scratchdir, FILE_LIST_GZ);
        } catch (IOException ex) {
            throw new InstallException(Msg.URL_FAILED, ex);
        }
    }

    /**
     * What are we using as a temp filename?
     */
    private static String getTempFileExtension(String host, String catalogDir) {
        return DOWNLOAD_PREFIX + host + catalogDir.replace('/', '_');
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof AbstractSwordInstaller)) {
            return false;
        }
        AbstractSwordInstaller that = (AbstractSwordInstaller) object;

        if (!equals(this.host, that.host)) {
            return false;
        }

        if (!equals(this.packageDirectory, that.packageDirectory)) {
            return false;
        }

        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(AbstractSwordInstaller myClass) {

        int ret = host.compareTo(myClass.host);
        if (ret != 0) {
            ret = packageDirectory.compareTo(myClass.packageDirectory);
        }
        return ret;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return host.hashCode() + packageDirectory.hashCode();
    }

    /**
     * Quick utility to check to see if 2 (potentially null) strings are equal
     */
    protected boolean equals(String string1, String string2) {
        if (string1 == null) {
            return string2 == null;
        }
        return string1.equals(string2);
    }

    /**
     * A map of the books in this download area
     */
    protected Map<String,Book> entries = new HashMap<String,Book>();

    /**
     * The remote hostname.
     */
    protected String host;

    /**
     * The remote proxy hostname.
     */
    protected String proxyHost;

    /**
     * The remote proxy port.
     */
    protected Integer proxyPort;

    /**
     * The directory containing zipped books on the <code>host</code>.
     */
    protected String packageDirectory = "";

    /**
     * The directory containing the catalog of all books on the
     * <code>host</code>.
     */
    protected String catalogDirectory = "";

    /**
     * The directory containing the catalog of all books on the
     * <code>host</code>.
     */
    protected String indexDirectory = "";

    /**
     * Do we need to reload the index file
     */
    protected boolean loaded;

    /**
     * The sword index file
     */
    protected static final String FILE_LIST_GZ = "mods.d.tar.gz";

    /**
     * The suffix of zip books on this server
     */
    protected static final String ZIP_SUFFIX = ".zip";

    /**
     * The log stream
     */
    protected static final Logger log = Logger.getLogger(AbstractSwordInstaller.class);

    /**
     * The relative path of the dir holding the search index files
     */
    protected static final String SEARCH_DIR = "search/jsword/L1";

    /**
     * When we cache a download index
     */
    protected static final String DOWNLOAD_PREFIX = "download-";

}
