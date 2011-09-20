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
 * ID: $Id:LuceneIndex.java 984 2006-01-23 14:18:33 -0500 (Mon, 23 Jan 2006) dmsmith $
 */
package net.bible.service.sword;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.DataPolice;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.LuceneIndex;
import org.crosswire.jsword.index.lucene.analysis.LuceneAnalyzer;
import org.crosswire.jsword.passage.Key;
import org.jdom.Element;

import android.util.Log;

/**
 * Implement the SearchEngine using Lucene as the search engine.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class PdaLuceneIndexCreator extends LuceneIndex {

	private static final String TAG = PdaLuceneIndexCreator.class.getName();
    /**
     * Generate an index to use, telling the job about progress as you go.
     * 
     * @throws BookException
     *             If we fail to read the index files
     */
    public PdaLuceneIndexCreator(Book book, URI storage, boolean create) throws BookException {
    	super(book, storage);
    	
        assert create;

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

        Analyzer analyzer = new LuceneAnalyzer(book);

        List errors = new ArrayList();
        File tempPath = new File(path + '.' + IndexStatus.CREATING.toString());

        try {
            synchronized (CREATING) {

                book.setIndexStatus(IndexStatus.CREATING);

                // An index is created by opening an IndexWriter with the create
                // argument set to true.
                // IndexWriter writer = new
                // IndexWriter(tempPath.getCanonicalPath(), analyzer, true);

                createIndex(book, job, analyzer, errors, tempPath);
                Log.d(TAG, "Job finished");

                job.setCancelable(false);
                if (!job.isFinished()) {
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
	 * @param book
	 * @param job
	 * @param analyzer
	 * @param errors
	 * @param tempPath
	 * @throws CorruptIndexException
	 * @throws LockObtainFailedException
	 * @throws IOException
	 * @throws BookException
	 */
	private void createIndex(Book book, Progress job, Analyzer analyzer,
			List errors, File tempPath) throws CorruptIndexException,
			LockObtainFailedException, IOException, BookException {
		Log.d(TAG, "Getting global key list for "+book.getName());
		Key allKeys = book.getGlobalKeyList();
		Log.d(TAG, "Got global key list");
		int numKeys = allKeys.getCardinality();
		Log.d(TAG, "Total keys:"+numKeys);
		int tenthCount = numKeys/10;
		
		Directory[] tempIndexFiles = new Directory[10]; 
		              
		for (int tenth=0; tenth<2; tenth++ ) {
			int startKey = tenth*tenthCount;
			int endKey = (tenth+1)*tenthCount;
			
			Key currentKeys = book.createEmptyKeyList();
			for (int i=startKey; i<endKey; i++ ) {
				Key key = allKeys.get(i);
				Log.d(TAG, "Adding key:"+key.getName());
				currentKeys.addAll(key);
			}
	
			Log.d(TAG, "1");
			// Create the index in core.
			final RAMDirectory ramDir = new RAMDirectory();
			Log.d(TAG, "2");
			IndexWriter writer = new IndexWriter(ramDir, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
			Log.d(TAG, "3");

			generateSearchIndexImpl(job, errors, writer, currentKeys , 0);
			Log.d(TAG, "4");

			job.setSectionName(UserMsg.OPTIMIZING.toString());
//			job.setWork(95);
	
			// Consolidate the index into the minimum number of files.
			// writer.optimize(); /* Optimize is done by addIndexes */
			writer.close();
			Log.d(TAG, "5");
	
			// Write the core index to disk.
			String tempFilePath = tempPath.getCanonicalPath()+tenth;
			Log.d(TAG, "temp index path:"+tempFilePath);
			final Directory destination = FSDirectory.open(new File(tempFilePath));
			Log.d(TAG, "6");
			IndexWriter fsWriter = new IndexWriter(destination, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
			Log.d(TAG, "7");
			fsWriter.addIndexesNoOptimize(new Directory[] {
			    ramDir
			});
			Log.d(TAG, "8");
			fsWriter.optimize();
			Log.d(TAG, "9");
			fsWriter.close();
			Log.d(TAG, "10");

			// Free up the space used by the ram directory
			ramDir.close();
			Log.d(TAG, "11");
			tempIndexFiles[tenth] = destination;
			Log.d(TAG, "12");
		}
		
		Log.d(TAG, "13");
		final Directory destination = FSDirectory.open(new File(tempPath.getCanonicalPath()));
		Log.d(TAG, "14");
		IndexWriter fsWriter = new IndexWriter(destination, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
		Log.d(TAG, "15");
		fsWriter.addIndexesNoOptimize(tempIndexFiles);
		Log.d(TAG, "16");
		fsWriter.optimize();
		Log.d(TAG, "17");
		fsWriter.close();
	}


    /**
     * Dig down into a Key indexing as we go.
     */
    private void generateSearchIndexImpl(Progress job, List errors, IndexWriter writer, Key key, int count) throws BookException, IOException {
        boolean hasStrongs = book.getBookMetaData().hasFeature(FeatureType.STRONGS_NUMBERS);
        boolean hasXRefs = book.getBookMetaData().hasFeature(FeatureType.SCRIPTURE_REFERENCES);
        boolean hasNotes = book.getBookMetaData().hasFeature(FeatureType.FOOTNOTES);
        boolean hasHeadings = book.getBookMetaData().hasFeature(FeatureType.HEADINGS);

        String oldRootName = ""; //$NON-NLS-1$
        int percent = 0;
        String rootName = ""; //$NON-NLS-1$
        BookData data = null;
        Key subkey = null;
        Element osis = null;

        // Set up for reuse.
        Document doc = new Document();
        Field keyField = new Field(FIELD_KEY, "", Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO); //$NON-NLS-1$
        Field bodyField = new Field(FIELD_BODY, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO); //$NON-NLS-1$
        Field strongField = new Field(FIELD_STRONG, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO); //$NON-NLS-1$
        Field xrefField = new Field(FIELD_XREF, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO); //$NON-NLS-1$
        Field noteField = new Field(FIELD_NOTE, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO); //$NON-NLS-1$
        Field headingField = new Field(FIELD_HEADING, "", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.NO); //$NON-NLS-1$

        int size = key.getCardinality();
        int subCount = count;
        Iterator it = key.iterator();
        while (it.hasNext()) {
            subkey = (Key) it.next();
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

                if (hasXRefs) {
                    addField(doc, xrefField, OSISUtil.getReferences(osis));
                }

                if (hasNotes) {
                    addField(doc, noteField, OSISUtil.getNotes(osis));
                }

                if (hasHeadings) {
                    addField(doc, headingField, OSISUtil.getHeadings(osis));
                }

                // Add the document if we added more than just the key.
                if (doc.getFields().size() > 1) {
                    writer.addDocument(doc);
                }

                // report progress
                rootName = subkey.getRootName();
                if (!rootName.equals(oldRootName)) {
                    oldRootName = rootName;
                    job.setSectionName(rootName);
                }

                subCount++;
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
     * The log stream
     */
    private static final Logger log = Logger.getLogger(PdaLuceneIndexCreator.class);

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
