package net.andbible.util;



import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.lucene.LucenePackage;
import org.crosswire.common.progress.JobManager;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.bridge.BookIndexer;
import org.crosswire.jsword.bridge.BookInstaller;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;

public class MJDIndexAll {

	private static final String REPOSITORY = "CrossWire";
//	private static final String REPOSITORY = "Xiphos";
//	private static final BookFilter BOOK_FILTER = BookFilters.getBibles();
	private static final BookFilter BOOK_FILTER = BookFilters.either(BookFilters.getBibles(), BookFilters.getCommentaries());

	//TODO this is awful but I need to figure out how to set it appropriately 
	private static final String LUCENE_INDEX_DIR = "C:/Documents and Settings/denha1m/Application Data/JSword/lucene/Sword";
	private static final File LUCENE_INDEX_DIR_FILE = new File(LUCENE_INDEX_DIR);
	
	private static final String LUCENE_ZIP_DIR = "C:/JSwordLuceneZips";
	private static final File LUCENE_ZIP_DIR_FILE = new File(LUCENE_ZIP_DIR);
	
    private static final int JAR_BUFFER_SIZE = 2048;
	
    public static void main(String[] args) {
    	MJDIndexAll indexAll = new MJDIndexAll();
    	indexAll.updateCachedRepoBookList();
//    	indexAll.setupDirs();
//    	indexAll.showInstalledBooks();
//    	indexAll.showRepoBooks();
//    	indexAll.installSingleBook("KJV");
//    	indexAll.installRepoBooks();
//    	indexAll.checkAllBooksInstalled();
//    	indexAll.manageCreateIndexes();
//    	indexAll.indexSingleBook("PolBibTysia");
    	
    	// 22/4/11 updates
    	indexAll.installAndIndexSingleBook("Clarke"); // somehow deleted
    	// new
    	indexAll.installAndIndexSingleBook("Antoniades");
    	// updated
    	indexAll.installAndIndexSingleBook("Elzevir"); //1.0 -> 1.1
    	indexAll.installAndIndexSingleBook("TR"); // 1.2 -> 2.1
		indexAll.installAndIndexSingleBook("SBLGNT"); // 1.2 -> 1.3
		indexAll.installAndIndexSingleBook("SBLGNTApp"); // 1.2 -> 1.3
		indexAll.installAndIndexSingleBook("Byz"); //1.10 -> 2.1
		indexAll.installAndIndexSingleBook("WHNU"); //1.10 -> 2.1
		indexAll.installAndIndexSingleBook("Luther"); //1.100322 -> 1.1

//bug, still need to create this index    	indexAll.installAndIndexSingleBook("SpaRV"); // 1.5-> 1.6
    }
    
    private void updateCachedRepoBookList() {
    	try {
	    	BookInstaller bookInstaller = new BookInstaller();
	    	bookInstaller.reloadBookList(REPOSITORY);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }

    private void installAndIndexSingleBook(String initials) {
    	deleteBook(initials);
    	installSingleBook(initials);
    	indexSingleBook(initials);
    }

    private void installSingleBook(String initials) {
    	BookInstaller bookInstaller = new BookInstaller();
        List<Book> books = (List<Book>)bookInstaller.getRepositoryBooks(REPOSITORY, BOOK_FILTER);
        
        for (Book book : books) {
        	if (initials.equalsIgnoreCase(book.getInitials())) {
            	String lang = book.getLanguage()==null? " " : book.getLanguage().getCode();
                System.out.println("Found in repo:"+lang+" "+book.getName());

                try {
                	if (Books.installed().getBook(book.getInitials()) != null) {
                        System.out.println("Already installed:"+book.getInitials()+":"+book.getName());
                	} else {
                        System.out.println("Downloading and installing:"+book.getInitials()+":"+book.getName());
                    	bookInstaller.installBook(REPOSITORY, book);
                    	waitToFinish();
                	}

                	Book installedBook = bookInstaller.getInstalledBook(book.getInitials());
                	if (installedBook==null) {
                		System.out.println("Not installed:"+book.getInitials()+" Name:"+book.getName());
                	}
 
                } catch (Exception e) {
                	System.out.println("Error installing:"+book.getInitials());
                	e.printStackTrace();
                }
        	}
        }

        
    }
    private void indexSingleBook(String initials) {
    	Book book = BookInstaller.getInstalledBook(initials);
    	indexBook(book);
    	createZipFile(book);
    }
    private void manageCreateIndexes() {
    	setupDirs();
    	//deleteAllBooks();
        showInstalledBooks();
    	showRepoBooks();
    	installRepoBooks();
    	indexAllBooks();
        addPropertiesFile();
    	createZipFiles();
    	checkAllBooksInstalled();
    }
    
    private void setupDirs() {
    	ensureDirExists(LUCENE_INDEX_DIR_FILE);
    	ensureDirExists(LUCENE_ZIP_DIR_FILE);
    }
    
    private void showInstalledBooks() {
        List<Book> books = (List<Book>)BookInstaller.getInstalledBooks();
        
        for (Book book : books) {
            System.out.println(book.getName());
        }
    }

    private void deleteAllBooks() {
        List<Book> books = (List<Book>)BookInstaller.getInstalledBooks();
        
        BookInstaller bookInstaller = new BookInstaller();
        for (Book book : books) {
        	deleteBook(book);
        }
    }

	private void deleteBook(String initials) {
		Book book = BookInstaller.getInstalledBook(initials);
		if (book!=null) {
			deleteBook(book);
		}
	}

	private void deleteBook(Book book) {
		System.out.println("Deleting:"+book.getInitials()+" name:"+book.getName());
		try {
		    IndexManager imanager = IndexManagerFactory.getIndexManager();
		    if (imanager.isIndexed(book)) {
		        imanager.deleteIndex(book);
		    }

		    book.getDriver().delete(book);
		} catch (Exception e) {
			System.out.println("Failed to delete "+book.getInitials()+":"+e.getMessage());
			e.printStackTrace();
		}
	}
    
    private void showRepoBooks() {
    	BookInstaller bookInstaller = new BookInstaller();
        List<Book> books = (List<Book>)bookInstaller.getRepositoryBooks(REPOSITORY, BOOK_FILTER);
        
        for (Book book : books) {
        	String lang = book.getLanguage()==null? " " : book.getLanguage().getCode();
            System.out.println(lang+" "+book.getName());
        }
    }
    
    private void installRepoBooks() {
    	BookInstaller bookInstaller = new BookInstaller();
        List<Book> books = (List<Book>)bookInstaller.getRepositoryBooks(REPOSITORY, BOOK_FILTER);
        
        for (Book book : books) {
            try {
            	if (Books.installed().getBook(book.getInitials()) != null) {
                    System.out.println("Already installed:"+book.getInitials()+":"+book.getName());
            	} else {
                    System.out.println("Downloading and installing:"+book.getInitials()+":"+book.getName());
                	bookInstaller.installBook(REPOSITORY, book);
                	waitToFinish();
            	}
            } catch (Exception e) {
            	System.out.println("Error installing:"+book.getInitials());
            	e.printStackTrace();
            }
        }
    }

    private void checkAllBooksInstalled() {
    	BookInstaller bookInstaller = new BookInstaller();
        List<Book> books = (List<Book>)bookInstaller.getRepositoryBooks(REPOSITORY, BOOK_FILTER);
        
        for (Book book : books) {
            try {
            	Book installedBook = bookInstaller.getInstalledBook(book.getInitials());
            	if (installedBook==null) {
            		System.out.println("Not installed:"+book.getInitials()+" Name:"+book.getName());
            	} else {
            		String version = (String)book.getProperty("Version");
            		String installedVersion = (String)installedBook.getBookMetaData().getProperty("Version");
            		if (!version.equals(installedVersion)) {
                		System.out.println("Incorrect version of "+book.getInitials()+" installed:"+installedVersion+" Repo:"+version);
            		} else {
            			System.out.println("Okay:"+book.getInitials()+" "+version);
            		}
            	}
            } catch (Exception e) {
            	System.out.println("Error installing:"+book.getInitials());
            	e.printStackTrace();
            }
        }
    }

    private void indexAllBooks() {
        List<Book> books = (List<Book>)BookInstaller.getInstalledBooks();
        
        for (Book book : books) {
        	indexBook(book);
        }
    }

	/**
	 * @param book
	 */
	private void indexBook(Book book) {
		System.out.println("Indexing:"+book.getInitials()+" name:"+book.getName());
		BookIndexer bookIndexer = new BookIndexer(book);
		if (!bookIndexer.isIndexed()) {
		    try {
		    	bookIndexer.createIndex();
		    	waitToFinish();
		    } catch (Exception e) {
		    	System.out.println(e.getMessage());
		    	e.printStackTrace();
		    }
		} else {
			System.out.println("Already indexed:"+book.getInitials());
		}
	}

    private void addPropertiesFile() {
        Properties indexProperties = new Properties();
        indexProperties.put("version", "1");
        indexProperties.put("java.specification.version", System.getProperty("java.specification.version"));
        indexProperties.put("java.vendor", System.getProperty("java.vendor"));
        indexProperties.put("lucene.specification.version",LucenePackage.get().getSpecificationVersion());
    	
        List<Book> books = (List<Book>)BookInstaller.getInstalledBooks();
        for (Book book : books) {
        	System.out.println("Adding properties file:"+book.getInitials()+" name:"+book.getName());
        	String initials = book.getInitials();
            String version = (String)book.getBookMetaData().getProperty("Version");
            String versionSuffix = version!=null ? "-"+version : "";
            File indexPropertiesFile = new File(LUCENE_INDEX_DIR_FILE, initials+"/index.properties");
            
            FileOutputStream fos = null;
            try {
	            fos = new FileOutputStream(indexPropertiesFile);
	            indexProperties.store(fos, null);
            } catch (IOException ioe) {
            	System.out.println(ioe.getMessage());
            	ioe.printStackTrace();
            } finally {
            	if (fos!=null) {
            		try {
            			fos.close();
            		} catch (IOException e2) {
            			e2.printStackTrace();
            		}
            	}
            }
        }
    }

    private void createZipFiles() {
    	
        List<Book> books = (List<Book>)BookInstaller.getInstalledBooks();
        for (Book book : books) {
        	createZipFile(book);
        }
    }

	/**
	 * @param book
	 */
	private void createZipFile(Book book) {
		System.out.println("Zipping file:"+book.getInitials()+" name:"+book.getName());
		String initials = book.getInitials();
		String version = (String)book.getBookMetaData().getProperty("Version");
		String versionSuffix = version!=null ? "-"+version : "";
		File zipFile = new File(LUCENE_ZIP_DIR_FILE, initials+versionSuffix+".zip");

		File indexDir = new File(LUCENE_INDEX_DIR_FILE, initials);
		
		createZipFile(zipFile, indexDir);
	}

    private void waitToFinish() {
        //Wait for job to start
        waitSec(3);
        //Wait for job to finish
        while (JobManager.getJobs().size()>0) {
            waitSec(3);
        }
    }

    private void waitSec(int secs) {
        try {
            Thread.sleep(1000*secs);
        } catch (InterruptedException e) {
            // ok to be interrupted
        }
    }
    
    private void ensureDirExists(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    private void createZipFile(File jarFile, File sourceDir) {
        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(jarFile);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            //out.setMethod(ZipOutputStream.DEFLATED);
            byte data[] = new byte[JAR_BUFFER_SIZE];
            // get a list of files from current directory
            File files[] = sourceDir.listFiles();

            for (int i = 0; i < files.length; i++) {
                System.out.println("Adding: " + files[i]);
                FileInputStream fi = new FileInputStream(files[i]);
                origin = new BufferedInputStream(fi, JAR_BUFFER_SIZE);
                ZipEntry entry = new ZipEntry(files[i].getName());
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, JAR_BUFFER_SIZE)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
