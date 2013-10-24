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

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.util.Version;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.bridge.BookIndexer;
import org.crosswire.jsword.bridge.BookInstaller;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;
import org.crosswire.jsword.index.IndexStatus;
//import org.apache.lucene.LucenePackage;

public class MJDIndexAll {

	private static final String REPOSITORY_CROSSWIRE = "CrossWire";
	private static final String REPOSITORY_CROSSWIRE_AV = "CrossWire AV";
	private static final String REPOSITORY_IBT = "IBT";
	private static final String REPOSITORY_XIPHOS = "Xiphos";
	private static final String REPOSITORY_CROSSWIRE_BETA = "Crosswire Beta";
	// Default repo used below
	private static final String REPOSITORY = REPOSITORY_CROSSWIRE_AV;
	
//	private static final BookFilter BOOK_FILTER = BookFilters.getDictionaries();
	private static final BookFilter BOOK_FILTER = BookFilters.either(BookFilters.getBibles(), BookFilters.getCommentaries());

	private static final String SWORD_BOOK_PATH = "C:/Sword/Books";
	
	//TODO this is awful but I need to figure out how to set it appropriately 
	private static final String LUCENE_INDEX_DIR = "C:/Users/Martin/Application Data/JSword/lucene/Sword";
	private static final File LUCENE_INDEX_DIR_FILE = new File(LUCENE_INDEX_DIR);
	
	private static final String LUCENE_ZIP_DIR = "C:/Sword/JSwordLuceneZips";
	private static final File LUCENE_ZIP_DIR_FILE = new File(LUCENE_ZIP_DIR);
	
    private static final int JAR_BUFFER_SIZE = 2048;
	
    public static void main(String[] args) {
    	try {
	    	MJDIndexAll indexAll = new MJDIndexAll();
	    	
	    	File bookDir = new File(SWORD_BOOK_PATH);
	    	File[] augmentPath = new File[1];
	    	augmentPath[0] = bookDir;
	    	SwordBookPath.setAugmentPath(augmentPath);
	    	SwordBookPath.setDownloadDir(bookDir);
	    	
	    	indexAll.updateCachedRepoBookList();
	//    	indexAll.validateIndex("OSMHB");
	//    	indexAll.validateAllIndexes();
	//    	indexAll.setupDirs();
	//    	indexAll.showInstalledBooks();
	//    	indexAll.showRepoBooks();
	//    	indexAll.deleteBook("StrongsHebrew");
	//    	indexAll.deleteBook("StrongsGreek");
	//    	indexAll.installSingleBook("ESV");
	//    	indexAll.installSingleBook("strongsrealhebrew");
	//    	indexAll.installSingleBook("strongsrealgreek");
	//    	indexAll.installSingleBook("StrongsHebrew");
	//    	indexAll.installSingleBook("StrongsGreek");
	//    	indexAll.installSingleBook("BDBGlosses_Strongs");
	//    	indexAll.installRepoBooks();
			boolean installAndIndex = false;
			indexAll.checkAllBooksInstalled(installAndIndex);
	//    	indexAll.manageCreateIndexes();
	//    	indexAll.indexSingleBook("KJV");
	    	
	    	// 22/4/11 updates
	//    	indexAll.installAndIndexSingleBook("Clarke"); // somehow deleted
	//    	// new
	//    	indexAll.installAndIndexSingleBook("Antoniades");
	//    	// updated
	//    	indexAll.installAndIndexSingleBook("Elzevir"); //1.0 -> 1.1
	//    	indexAll.installAndIndexSingleBook("TR"); // 1.2 -> 2.1
	//		indexAll.installAndIndexSingleBook("SBLGNT"); // 1.2 -> 1.3
	//		indexAll.installAndIndexSingleBook("SBLGNTApp"); // 1.2 -> 1.3
	//		indexAll.installAndIndexSingleBook("Byz"); //1.10 -> 2.1
	//		indexAll.installAndIndexSingleBook("WHNU"); //1.10 -> 2.1
	//		indexAll.installAndIndexSingleBook("Luther"); //1.100322 -> 1.1
	
	    	//uploaded 14.5.2010
	//    	indexAll.installAndIndexSingleBook("PorLivre"); // 1.5-> 1.6
	//    	indexAll.installAndIndexSingleBook("SpaRV"); // 1.5-> 1.6
	
	    	//uploaded 28/4/2011
	    	//Need to fix indexes without Strong's
	//    	indexAll.indexSingleBook("LXX");
	//    	indexAll.indexSingleBook("ABP");
	//    	indexAll.indexSingleBook("ABPGrk");
	//    	indexAll.indexSingleBook("ChiUn");
	//    	indexAll.indexSingleBook("ChiUns");
	    	//uploaded 14.5.2010
	//    	indexAll.installAndIndexSingleBook("RUSVZh");  //is this broken
	
	    	// 8/6/11 books without Feature=Strongs but with Strongs
	//    	indexAll.indexSingleBook("OSMHB");
	//    	indexAll.indexSingleBook("RWebster");
	//    	indexAll.indexSingleBook("RST");
	//    	indexAll.indexSingleBook("SpaTDP");
	//    	indexAll.indexSingleBook("Byz");
	    	
	    	// 11/7/11
	//    	indexAll.installAndIndexSingleBook("Sorani");
	//    	indexAll.installAndIndexSingleBook("GerNeUe"); //up to ver 1.4
	//    	indexAll.installAndIndexSingleBook("AraNAV");
	//    	indexAll.installAndIndexSingleBook("UrduGeo"); //up to 1.1 (some time ago)
	//    	indexAll.installAndIndexSingleBook("WelBeiblNet");
	//    	indexAll.installAndIndexSingleBook("Azeri");
	    	
	    	// 19/8/2011
	//    	indexAll.installAndIndexSingleBook("PorCapNT");
	//    	indexAll.installAndIndexSingleBook("GerLut1545");
	
	    	// 11/9/2011
	    	// Crosswire Beta
	//    	indexAll.installAndIndexSingleBook("JapBungo");
	//    	indexAll.installAndIndexSingleBook("JapDenmo");
	//    	indexAll.installAndIndexSingleBook("JapKougo");
	//    	indexAll.installAndIndexSingleBook("JapMeiji");
	//    	indexAll.installAndIndexSingleBook("JapRaguet");
	//    	indexAll.installAndIndexSingleBook("CalvinCommentaries");
	    	// Xiphos - manually download first
	//    	indexAll.indexSingleBook("ChiPinyin");
	
	//		1/11/2011  	
	//    	indexAll.installAndIndexSingleBook("Latvian");
	//    	indexAll.installAndIndexSingleBook("AraSVD");
	//    	indexAll.installAndIndexSingleBook("Lithuanian");
	    
	    	// 2/1/12
	//    	indexAll.installAndIndexSingleBook("FrePGR");
	//    	indexAll.installAndIndexSingleBook("NorBroed");
	//    	indexAll.installAndIndexSingleBook("TurNTB");
	
	    	//19/1/2012
	//    	indexAll.installAndIndexSingleBook("WEBME");
	//    	indexAll.installAndIndexSingleBook("WEBBE");
	//    	indexAll.installAndIndexSingleBook("FreCJE");
	//    	indexAll.installAndIndexSingleBook("FarOPV");
	//    	indexAll.installAndIndexSingleBook("FrePGR");
	
	//    	indexAll.installAndIndexSingleBook("WEB");
	//    	indexAll.installAndIndexSingleBook("EMTV");
	    	
	    	//25/1/2012
	//    	indexAll.installAndIndexSingleBook("SpaRVG");
	//    	indexAll.installAndIndexSingleBook("GerNeUe");
	//    	indexAll.installAndIndexSingleBook("NorSMB");
	//    	indexAll.installAndIndexSingleBook("WEB");
	//    	indexAll.installAndIndexSingleBook("WEBME");
	//    	indexAll.installAndIndexSingleBook("WEBBE");
	    	
	//    	indexAll.installSingleBook("HunUj");
	    	
	    	// 17/12/2012
	//    	indexAll.installAndIndexSingleBook("FrePGR");
	//    	indexAll.installAndIndexSingleBook("BurJudson");
	//    	indexAll.installAndIndexSingleBook("GerNeUe");
	//    	indexAll.installAndIndexSingleBook("KhmerNT");
	//    	indexAll.installAndIndexSingleBook("ThaiKJV");
	//    	indexAll.installRepoBooks();
	
	    	//25/2/12
	//    	indexAll.installAndIndexSingleBook("AraNAV");
	//    	indexAll.installAndIndexSingleBook("Azeri");
	//    	indexAll.installAndIndexSingleBook("FreBBB");
	//    	indexAll.installAndIndexSingleBook("FrePGR");
	//    	indexAll.installAndIndexSingleBook("SomKQA");
	//    	indexAll.installAndIndexSingleBook("Sorani");
	//    	indexAll.installAndIndexSingleBook("WelBeiblNet");
	    	
	//    	indexAll.installAndIndexSingleBook("MonKJV");
	    	
	    	//25/6/2012
	//    	indexAll.installAndIndexSingleBook("BretonNT");
	//    	indexAll.installAndIndexSingleBook("PorLivre");
	//    	indexAll.installAndIndexSingleBook("OEBcth");
	//    	indexAll.installAndIndexSingleBook("OEB");
	//    	indexAll.installAndIndexSingleBook("LEB");
	//    	indexAll.installAndIndexSingleBook("KorRV");
	    	
	    	//3/7/2012
	//    	indexAll.installAndIndexSingleBook("Pohnpeian");
	//    	indexAll.installAndIndexSingleBook("LEB");
	//    	indexAll.installAndIndexSingleBook("MonKJV");
	//    	indexAll.installAndIndexSingleBook("Che1860");
	    	
	//    	//18/08/2012
	//    	indexAll.installAndIndexSingleBook("BBE");
	//    	indexAll.installAndIndexSingleBook("ACV");
	//    	indexAll.installAndIndexSingleBook("IriODomhnuill");
	//    	indexAll.installAndIndexSingleBook("ABP");
	//    	indexAll.installAndIndexSingleBook("FrePGR");
	//    	indexAll.installAndIndexSingleBook("Pohnpeian");
	//    	indexAll.installAndIndexSingleBook("PorAR");
	//    	indexAll.installAndIndexSingleBook("PorLivre");
	//    	indexAll.installAndIndexSingleBook("FreBBB");
	//    	indexAll.installAndIndexSingleBook("Geez");
	//    	indexAll.installAndIndexSingleBook("KorHKJV");
	//    	indexAll.installAndIndexSingleBook("KJVPCE");
	//    	indexAll.installAndIndexSingleBook("PolGdanska");
	//    	indexAll.installAndIndexSingleBook("SpaRV1909");
	//    	indexAll.installAndIndexSingleBook("LEB");
	//    	indexAll.installAndIndexSingleBook("WEBME");
	 //   	indexAll.installAndIndexSingleBook("QuotingPassages");
	
	    	// 14/11/12
	//    	indexAll.installAndIndexSingleBook("HinERV");
	//    	indexAll.installAndIndexSingleBook("MalBSI");
	//    	indexAll.installAndIndexSingleBook("GerNeUe");
	    	
	    	// 19/1/13
	    	// new
//	    	indexAll.installAndIndexSingleBook("Dari");
//	    	indexAll.installAndIndexSingleBook("sml_BL_2008");
//	    	indexAll.installAndIndexSingleBook("FarHezareNoh");
// 			indexAll.installAndIndexSingleBook("FarTPV");
// 			indexAll.installAndIndexSingleBook("UrduGeoDeva");
// 			indexAll.installAndIndexSingleBook("Burkitt");
// 			// updated
// 			indexAll.installAndIndexSingleBook("FinPR92"); //Errors
// 			indexAll.installAndIndexSingleBook("UrduGeo");
// 			indexAll.installAndIndexSingleBook("RWP");
	    	// 8/2/13
//	    	indexAll.installAndIndexSingleBook("HinERV");
	    	
//	    	indexAll.installAndIndexSingleBook("RusSynodal");
	    	
	    	// Crosswire AV
//	    	indexAll.installAndIndexAllRepoBooks();

//			indexAll.installAndIndexSingleBook("SBLGNT");
//			indexAll.installAndIndexSingleBook("UrduGeo");
//			indexAll.installAndIndexSingleBook("UrduGeoDeva");
			
			//2013-09-11
//			indexAll.installAndIndexSingleBook("Chamorro");
//			indexAll.installAndIndexSingleBook("GerElb1871");
//			indexAll.installAndIndexSingleBook("GerSch");
//			indexAll.installAndIndexSingleBook("SpaVNT");
//			indexAll.installAndIndexSingleBook("Swe1917");
//			indexAll.installAndIndexSingleBook("UKJV");
//			indexAll.installAndIndexSingleBook("Viet");
//			indexAll.installAndIndexSingleBook("sml_BL_2008");
			//IBT
//			indexAll.installAndIndexSingleBook("KAZ"); //errors like org.crosswire.jsword.passage.NoSuchVerseException: Verse should be between 0 and 6 for Isaiah 20 (given 15).
//			indexAll.installAndIndexSingleBook("KYLSA");
//			indexAll.installAndIndexSingleBook("RSP");
//			indexAll.installAndIndexSingleBook("UZV");
//			indexAll.installAndIndexSingleBook("UZVL");
			//AV
//			indexAll.installAndIndexSingleBook("FreKhan");
//			indexAll.installAndIndexSingleBook("HunUj");
//			indexAll.installAndIndexSingleBook("VulgClementine");
//			indexAll.installAndIndexSingleBook("VulgConte");
//			indexAll.installAndIndexSingleBook("VulgHetzenauer");
//			indexAll.installAndIndexSingleBook("VulgSistine");
			
			//2013-10-18 Crosswire AV
//			indexAll.installAndIndexSingleBook("GerLut1545");
//			indexAll.installAndIndexSingleBook("WLC");

//			indexAll.installAndIndexSingleBook("KorRV");
//			indexAll.installAndIndexSingleBook("OSHB");
			//indexAll.installAndIndexSingleBook("RusSynodalLIO");
			
			//CW
//			indexAll.installAndIndexSingleBook("KJV");
//			indexAll.installAndIndexSingleBook("AB");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
	public void validateAllIndexes() {
		List<Book> bibles = Books.installed().getBooks(BookFilters.getBibles());
		
		for (Book book : bibles) {
			validateIndex(book);
		}
	}

	public void validateIndex(String bookInitials) {
		validateIndex(Books.installed().getBook(bookInitials));
	}
	
	public void validateIndex(Book book) {
		try {
			if (hasStrongs(book)) {
				if (!book.getIndexStatus().equals(IndexStatus.DONE)) {
					System.out.println("Unindexed:"+book);
				} else {
					if (!checkStrongs(book)) {
						System.out.println("No refs returned in"+book.getInitials());
					} else {
						System.out.println("Ok:"+book.getInitials());
					}
	//					assertTrue("No refs returned in"+book.getInitials(), resultsH.getCardinality()>0 || resultsG.getCardinality()>0);
				}
			}
		} catch (Exception e) {
			System.out.println("Error:"+book.getInitials()+":"+e.getMessage());
		}

	}

	private boolean hasStrongs(Book book) {
		Object globalOptionFilter = book.getBookMetaData().getProperty("GlobalOptionFilter");
		return globalOptionFilter==null ? false : globalOptionFilter.toString().contains("Strongs"); 
	}

	/** ensure a book is indexed and the index contains typical Greek or Hebrew Strongs Numbers
	 */
	private boolean checkStrongs(Book bible) {
		try {
			return bible.getIndexStatus().equals(IndexStatus.DONE) &&
				   (bible.find("+[Gen 1:1] strong:h7225").getCardinality()>0 ||
					bible.find("+[John 1:1] strong:g746").getCardinality()>0 ||
					bible.find("+[Gen 1:1] strong:g746").getCardinality()>0);
		} catch (BookException be) {
			System.out.println("Error checking strongs numbers: "+ be.getMessage());
			return false;
		}
	}

	private void updateCachedRepoBookList() {
    	try {
	    	BookInstaller bookInstaller = new BookInstaller();
	    	bookInstaller.reloadBookList(REPOSITORY);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }

    private void installAndIndexAllRepoBooks() {
    	BookInstaller bookInstaller = new BookInstaller();
        List<Book> books = (List<Book>)bookInstaller.getRepositoryBooks(REPOSITORY, BOOK_FILTER);
        
        for (Book book : books) {
        	installAndIndexSingleBook(book.getInitials());
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
                	if (bookInstaller.getInstalledBook(book.getInitials())!=null) {
                		System.out.println("Installer thinks Already installed:"+book.getInitials()+":"+book.getName());
                	}
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
    	try {
	    	Book book = BookInstaller.getInstalledBook(initials);
	    	
		    IndexManager imanager = IndexManagerFactory.getIndexManager();
		    if (imanager.isIndexed(book)) {
		        imanager.deleteIndex(book);
		    }
	    	
	    	indexBook(book);
	    	createZipFile(book);
        } catch (Exception e) {
        	System.out.println("Error indexing:"+initials);
        	e.printStackTrace();
        }

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
    	checkAllBooksInstalled(false);
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

    private void checkAllBooksInstalled(boolean installAndIndex) {
    	BookInstaller bookInstaller = new BookInstaller();
        List<Book> books = (List<Book>)bookInstaller.getRepositoryBooks(REPOSITORY, BOOK_FILTER);
        
        for (Book book : books) {
            try {
				boolean isOkay = false;
            	Book installedBook = bookInstaller.getInstalledBook(book.getInitials());
            	if (installedBook==null) {
            		System.out.println("Not installed:"+book.getInitials()+" Name:"+book.getName());
            	} else {
            		Version versionObj = (Version)book.getProperty("Version");
           			String version = versionObj==null ? "No version" : versionObj.toString();
           			
           			Version installedVersionObj = (Version)installedBook.getBookMetaData().getProperty("Version");
            		String installedVersion = installedVersionObj==null ? "No version" : installedVersionObj.toString();
            		if (!version.equals(installedVersion)) {
                		System.out.println("Incorrect version of "+book.getInitials()+" installed:"+installedVersion+" Repo:"+version);
            		} else {
            			System.out.println("Okay:"+book.getInitials()+" "+version);
						isOkay = true;
            		}
					if (installAndIndex && !isOkay) {
						installAndIndexSingleBook(book.getInitials());
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
		try {
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
		} catch (Exception e) {
			System.out.println("Failed to delete "+book.getInitials()+":"+e.getMessage());
			e.printStackTrace();
		}
	}

    private void addPropertiesFile() {
        Properties indexProperties = new Properties();
        indexProperties.put("version", "1");
        indexProperties.put("java.specification.version", System.getProperty("java.specification.version"));
        indexProperties.put("java.vendor", System.getProperty("java.vendor"));
//        indexProperties.put("lucene.specification.version",LucenePackage.get().getSpecificationVersion());
    	
        List<Book> books = (List<Book>)BookInstaller.getInstalledBooks();
        for (Book book : books) {
        	System.out.println("Adding properties file:"+book.getInitials()+" name:"+book.getName());
        	String initials = book.getInitials();
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
		String version = book.getBookMetaData().getProperty("Version").toString();
		String versionSuffix = version!=null ? "-"+version : "";
		File zipFile = new File(LUCENE_ZIP_DIR_FILE, initials+versionSuffix+".zip");

		File indexDir = new File(LUCENE_INDEX_DIR_FILE, initials);
		
		createZipFile(zipFile, indexDir);
	}

    private void waitToFinish() {
        //Wait for job to start
        waitSec(3);
        //Wait for job to finish
        while (JobManager.getJobCount()>0) {
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
