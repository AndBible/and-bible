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
import java.util.Iterator;
import java.util.List;

import net.bible.service.sword.Logger;
import net.bible.service.sword.SwordApi;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.SimpleAnalyzer;
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
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.DataPolice;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.PassageKeyFactory;

/**
 * Implement the SearchEngine using Lucene as the search engine.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class PdaLuceneIndexCreator {
    /*
     * The following fields are named the same as Sword in the hopes of sharing
     * indexes.
     */
    /**
     * The Lucene field for the osisID
     */
    public static final String FIELD_KEY = "key"; //$NON-NLS-1$

    /**
     * The Lucene field for the text contents
     */
    public static final String FIELD_BODY = "content"; //$NON-NLS-1$

    /**
     * The Lucene field for the strong numbers
     */
    public static final String FIELD_STRONG = "strong"; //$NON-NLS-1$

    /**
     * The Lucene field for headings
     */
    public static final String FIELD_HEADING = "heading"; //$NON-NLS-1$

    /**
     * The Lucene field for cross references
     */
    public static final String FIELD_XREF = "xref"; //$NON-NLS-1$

    /**
     * The Lucene field for the notes
     */
    public static final String FIELD_NOTE = "note"; //$NON-NLS-1$

    /** we are on a device with limited ram so don't use too much */
    private static final int MAX_RAM_BUFFER_SIZE_MB = 4;
    
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
            throw new BookException(UserMsg.LUCENE_INIT, ex);
        }

        // Indexing the book is a good way to police data errors.
        DataPolice.setBook(book.getBookMetaData());

        Progress job = JobManager.createJob(UserMsg.INDEX_START.toString(book.getInitials()), Thread.currentThread(), false);

        IndexStatus finalStatus = IndexStatus.UNDONE;

        Analyzer analyzer = new SimpleAnalyzer();//LuceneAnalyzer(book);

        List errors = new ArrayList();
        File tempPath = new File(path + '.' + IndexStatus.CREATING.toString());

        try {
            synchronized (CREATING) {

                book.setIndexStatus(IndexStatus.CREATING);

               // Create the index in core.
                Directory destination = FSDirectory.open(new File(tempPath.getCanonicalPath()));
                IndexWriter writer = new IndexWriter(destination, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
                writer.setRAMBufferSizeMB(MAX_RAM_BUFFER_SIZE_MB);
                logger.debug("Beginning indexing "+book.getName());
                try {
	                Key keyList = PassageKeyFactory.instance().getGlobalKeyList(); //getKey("Genesis");
	                generateSearchIndexImpl(job, errors, writer, keyList, 0);
                } catch (Exception e) {
                	e.printStackTrace();
                }
                logger.info("Finished indexing "+book.getName()+" starting optimisation");

                job.setSectionName(UserMsg.OPTIMIZING.toString());
                job.setWork(95);

                // Consolidate the index into the minimum number of files.
                // writer.optimize(); /* Optimize is done by addIndexes */
                writer.optimize();
                writer.close();

                job.setCancelable(false);
                if (!job.isFinished()) {
                	logger.debug("Renaming "+tempPath+" to "+finalPath);
                    if (!tempPath.renameTo(finalPath)) {
                        throw new BookException(UserMsg.INSTALL_FAIL);
                    }
                }

                if (finalPath.exists()) {
                    finalStatus = IndexStatus.DONE;
                }

                if (!errors.isEmpty()) {
                    StringBuffer buf = new StringBuffer();
                    Iterator iter = errors.iterator();
                    while (iter.hasNext()) {
                        buf.append(iter.next());
                        buf.append('\n');
                    }
                    Reporter.informUser(this, UserMsg.BAD_VERSE, buf);
                }
            }
        } catch (IOException ex) {
            job.cancel();
            throw new BookException(UserMsg.LUCENE_INIT, ex);
        } finally {
            book.setIndexStatus(finalStatus);
            job.done();
        }
    }


    /**
     * Dig down into a Key indexing as we go.
     */
    private void generateSearchIndexImpl(Progress job, List errors, IndexWriter writer, Key key, int count) throws BookException, IOException {
        logger.debug("Generating search Index");

        String oldRootName = ""; //$NON-NLS-1$
        int percent = 0;
        String rootName = ""; //$NON-NLS-1$
        BookData data = null;
        Key subkey = null;
        String canonicalText = null;

        // Set up for reuse.
        Document doc = new Document();
        Field keyField = new Field(FIELD_KEY, "", Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO); //$NON-NLS-1$
        Field bodyField = new Field(FIELD_BODY, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO); //$NON-NLS-1$

        int size = key.getCardinality();
        logger.debug("Number of keys:"+size);
        int subCount = count;
        Iterator it = key.iterator();
        while (it.hasNext()) {
            subkey = (Key) it.next();
            if (subkey.canHaveChildren()) {
                generateSearchIndexImpl(job, errors, writer, subkey, subCount);
            } else {
                // Set up DataPolice for this key.
                DataPolice.setKey(subkey);

                try {
                	canonicalText = SwordApi.getInstance().getCanonicalText(book, subkey, 1);
                } catch (NoSuchKeyException e) {
                	logger.warn("No such key:"+subkey.getName());
                    errors.add(subkey);
                    continue;
                }

                // Remove all fields from the document
                doc.getFields().clear();

                // Do the actual indexing
                // Always add the key
                keyField.setValue(subkey.getOsisRef());
                doc.add(keyField);

                addField(doc, bodyField, canonicalText);

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
                }
                
                percent = 95 * subCount / size;

                job.setWork(percent);

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
