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
 * ID: $Id:PdaLuceneIndexCreator.java 984 2006-01-23 14:18:33 -0500 (Mon, 23 Jan 2006) dmsmith $
 */
package org.crosswire.jsword.index.lucene;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.bible.service.common.CommonUtils;
import net.bible.service.sword.Logger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.JSMsg;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.DataPolice;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.analysis.LuceneAnalyzer;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.jdom.Element;

/**
 * Implement the SearchEngine using Lucene as the search engine.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class PdaLuceneIndexCreator {
    /*
     * The following fields are named the same as Sword in the hopes of sharing
     * indexes.
     */
    /**
     * The Lucene field for the osisID
     */
    public static final String FIELD_KEY = "key";

    /**
     * The Lucene field for the text contents
     */
    public static final String FIELD_BODY = "content";

    /**
     * The Lucene field for the strong numbers
     */
    public static final String FIELD_STRONG = "strong";

    /** we are on a device with limited ram so don't use too much */
    private static final int MAX_RAM_BUFFER_SIZE_MB = 1;
    
    private static final String TAG = "PdaLuceneIndexCreator";
    
    private static final Logger logger = new Logger(TAG);

    /**
     * Generate an index to use, telling the job about progress as you go.
     * 
     * @throws BookException
     *             If we fail to read the index files
     */
    public PdaLuceneIndexCreator(Book book, URI storage, boolean create) throws BookException {
        assert create;
    	logger.info("Index target dir:"+storage.getPath());

        this.book = book;
        File finalPath = null;
        try {
            finalPath = NetUtil.getAsFile(storage);
            this.path = finalPath.getCanonicalPath();
        } catch (IOException ex) {
            // TRANSLATOR: Error condition: Could not initialize a search index. Lucene is the name of the search technology being used.
            throw new BookException(JSMsg.gettext("Failed to initialize Lucene search engine."), ex);
        }

        // Indexing the book is a good way to police data errors.
        DataPolice.setBook(book.getBookMetaData());

        // TRANSLATOR: Progress label indicating the start of indexing. {0} is a placeholder for the book's short name.
        String jobName = JSMsg.gettext("Creating index. Processing {0}", book.getInitials());
        Progress job = JobManager.createJob(jobName, Thread.currentThread());
        job.beginJob(jobName);

        IndexStatus finalStatus = IndexStatus.UNDONE;
        List<Key> errors = new ArrayList<Key>();
        File tempPath = new File(path + '.' + IndexStatus.CREATING.toString());

        try {
        	// this can throw an error if indexing is misconfigured so needs to be in the try/catch block
	        Analyzer analyzer = new LuceneAnalyzer(book);

            synchronized (CREATING) {

                book.setIndexStatus(IndexStatus.CREATING);

               // Create the index in core.
                IndexWriter writer = null;
                try {
	                Directory destination = FSDirectory.open(new File(tempPath.getCanonicalPath()));
	                writer = new IndexWriter(destination, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
	                writer.setRAMBufferSizeMB(MAX_RAM_BUFFER_SIZE_MB);
	                logger.debug("Beginning indexing "+book.getName());
	                try {
	                	Key keyList = null;
	                	if (book.getBookCategory().equals(BookCategory.BIBLE)) {
	                		// this method is so much faster than getGlobalKeyList but not accurate e.g. some bibles are only NT
	                		keyList = PassageKeyFactory.instance().getGlobalKeyList();
	                	} else {
	                		keyList = book.getGlobalKeyList();
	                	}
		                generateSearchIndexImpl(job, errors, writer, keyList, 0);
	                } catch (Exception e) {
	                	e.printStackTrace();
                        // TRANSLATOR: The search index could not be moved to it's final location.
                        throw new BookException(JSMsg.gettext("Installation failed."));
	                }
	                logger.info("Finished indexing "+book.getName()+" starting optimisation");
	
	                // TRANSLATOR: Progress label for optimizing a search index. This may take a bit of time, so we have a label for it.
	                job.setSectionName(JSMsg.gettext("Optimizing"));
	                // must be 1 more than 95 for the notification to be sent through to the listener
	                job.setWork(96);
	
	                // Consolidate the index into the minimum number of files.
	                // writer.optimize(); /* Optimize is done by addIndexes */
	                writer.optimize();
                } finally {
                	// writer must be closed even on error to release the Lucene Lock
                	if (writer!=null) {
                		writer.close();
                	}
                }

                job.setCancelable(false);
                if (!job.isFinished()) {
                	logger.debug("Renaming "+tempPath+" to "+finalPath);
                    if (!tempPath.renameTo(finalPath)) {
                        // TRANSLATOR: The search index could not be moved to it's final location.
                        throw new BookException(JSMsg.gettext("Installation failed."));
                    }
                }

                if (finalPath.exists()) {
                    finalStatus = IndexStatus.DONE;
                }

                if (!errors.isEmpty()) {
                    StringBuilder buf = new StringBuilder();
                    for (Key error : errors) {
                        buf.append(error);
                        buf.append('\n');
                    }
                    // TRANSLATOR: It is likely that one or more verses could not be indexed due to errors in those verses.
                    // This message gives a listing of them to the user.
                    Reporter.informUser(this, JSMsg.gettext("The following verses have errors and could not be indexed\n{0}", buf));
                }
            }
        } catch (Exception ex) {
            job.cancel();
            // TRANSLATOR: Common error condition: Some error happened while creating a search index.
            throw new BookException(JSMsg.gettext("Failed to initialize Lucene search engine."), ex);
        } finally {
            book.setIndexStatus(finalStatus);
            job.done();
            // ensure the temp path is gone - errors can leave it there and cause further problems
            CommonUtils.deleteDirectory(tempPath);
        }
    }


    /**
     * Dig down into a Key indexing as we go.
     */
    private void generateSearchIndexImpl(Progress job, List<Key> errors, IndexWriter writer, Key key, int count) throws BookException, IOException {
        logger.debug("Generating search Index");
        boolean hasStrongs = book.getBookMetaData().hasFeature(FeatureType.STRONGS_NUMBERS);

        String oldRootName = "";
        int percent = 0;
        String rootName = "";
        BookData data = null;
        Element osis = null;

        // Set up for reuse.
        Document doc = new Document();
        Field keyField = new Field(FIELD_KEY, "", Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO);
        Field bodyField = new Field(FIELD_BODY, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO);
        Field strongField = new Field(FIELD_STRONG, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO);

        int size = key.getCardinality();
        logger.debug("Number of keys:"+size);
        int subCount = count;
        for (Key subkey : key) {
            if (subkey.canHaveChildren()) {
                generateSearchIndexImpl(job, errors, writer, subkey, subCount);
            } else {
                // Set up DataPolice for this key.
                DataPolice.setKey(subkey);

                data = new BookData(book, subkey);
                osis = null;

                try {
                    osis = data.getOsisFragment();
                } catch (BookException e) {
                    errors.add(subkey);
                    continue;
                }

                // Remove all fields from the document
                doc.getFields().clear();

                // Do the actual indexing
                // Always add the key
                keyField.setValue(subkey.getOsisRef());
                doc.add(keyField);

                addField(doc, bodyField, OSISUtil.getCanonicalText(osis));

                if (hasStrongs) {
                    addField(doc, strongField, OSISUtil.getStrongsNumbers(osis));
                }

                // Add the document if we added more than just the key.
                if (doc.getFields().size() > 1) {
                    writer.addDocument(doc);
                }

                 subCount++;

                // report progress but not all the time for efficiency
                if (subCount%50 ==0) {
	                rootName = subkey.getRootName();
	                if (!rootName.equals(oldRootName)) {
	                    oldRootName = rootName;
	                    job.setSectionName(rootName);
	                }
	                percent = 95 * subCount / size;
	                job.setWork(percent);

	                // and force a garbage collect every so often
	                System.gc();
                }

                // This could take a long time ...
                Thread.yield();
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
    }

    private void addField(Document doc, Field field, String text) {
        if (text != null && text.length() > 0) {
            field.setValue(text);
            doc.add(field);
        }
    }

    /**
     * A synchronization lock point to prevent us from doing 2 index runs at a
     * time.
     */
    private static final Object CREATING = new Object();

    /**
     * Are we active
     */
    private boolean active;

    /**
     * The Book that we are indexing
     */
    protected Book book;

    /**
     * The location of this index
     */
    private String path;

    /**
     * The Lucene directory for the path.
     */
    protected Directory directory;

    /**
     * The Lucene search engine
     */
    protected Searcher searcher;
}
