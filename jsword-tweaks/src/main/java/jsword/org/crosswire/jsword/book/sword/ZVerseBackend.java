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
 * ID: $Id: ZVerseBackend.java 2099 2011-03-07 17:13:00Z dmsmith $
 */
package org.crosswire.jsword.book.sword;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;

import org.crosswire.common.activate.Activator;
import org.crosswire.common.activate.Lock;
import org.crosswire.common.compress.CompressorType;
import org.crosswire.common.util.FileUtil;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.jsword.JSMsg;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.DataPolice;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

/**
 * A backend to read compressed data verse based files. While the text file
 * contains data compressed with ZIP or LZSS, it cannot be uncompressed using a
 * stand alone zip utility, such as WinZip or gzip. The reason for this is that
 * the data file is a concatenation of blocks of compressed data.
 * 
 * <p>
 * The blocks can either be "b", book (aka testament); "c", chapter or "v",
 * verse. The choice is a matter of trade offs. The program needs to uncompress
 * a block into memory. Having it at the book level is very memory expensive.
 * Having it at the verse level is very disk expensive, but takes the least
 * amount of memory. The most common is chapter.
 * </p>
 * 
 * <p>
 * In order to find the data in the text file, we need to find the block. The
 * first index (comp) is used for this. Each verse is indexed to a tuple (block
 * number, verse start, verse size). This data allows us to find the correct
 * block, and to extract the verse from the uncompressed block, but it does not
 * help us uncompress the block.
 * </p>
 * 
 * <p>
 * Once the block is known, then the next index (idx) gives the location of the
 * compressed block, its compressed size and its uncompressed size.
 * </p>
 * 
 * <p>
 * There are 3 files for each testament, 2 (comp and idx) are indexes into the
 * third (text) which contains the data. The key into each index is the verse
 * index within that testament, which is determined by book, chapter and verse
 * of that key.
 * </p>
 * 
 * <p>
 * All numbers are stored 2-complement, little endian.
 * </p>
 * <p>
 * Then proceed as follows, at all times working on the set of files for the
 * testament in question:
 * </p>
 * 
 * <pre>
 * in the comp file, seek to the index * 10
 * read 10 bytes.
 * the block-index is the first 4 bytes (32-bit number)
 * the next bytes are the verse offset and length of the uncompressed block.
 * in the idx file seek to block-index * 12
 * read 12 bytes
 * the text-block-index is the first 4 bytes
 * the data-size is the next 4 bytes
 * the uncompressed-size is the next 4 bytes
 * in the text file seek to the text-block-index
 * read data-size bytes
 * decipher them if they are encrypted
 * unGZIP them into a byte array of uncompressed-size
 * </pre>
 * 
 * TODO(DM): Testament 0 is used to index an README file for the bible. At this
 * time it is ignored.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class ZVerseBackend extends AbstractBackend {
    private static final String SUFFIX_COMP = "v";
    private static final String SUFFIX_INDEX = "s";
    private static final String SUFFIX_PART1 = "z";
    private static final String SUFFIX_TEXT = "z";

    /**
     * Simple ctor
     */
    public ZVerseBackend(SwordBookMetaData sbmd, BlockType blockType) {
        super(sbmd);
        this.blockType = blockType;
        // Some commentaries like MHC show the same content for several adjacent verses, but merged verses should not be duplicated
        this.isPreventDuplicateVerseContent = BookCategory.BIBLE.equals(sbmd.getBookCategory());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.common.activate.Activatable#activate(org.crosswire.common
     * .activate.Lock)
     */
    public final void activate(Lock lock) {
        try {
            if (idxFile[SwordConstants.TESTAMENT_OLD] == null) {
                URI path = getExpandedDataPath();
                String otAllButLast = NetUtil.lengthenURI(path, File.separator + SwordConstants.FILE_OT + '.' + blockType.getIndicator() + SUFFIX_PART1)
                        .getPath();
                idxFile[SwordConstants.TESTAMENT_OLD] = new File(otAllButLast + SUFFIX_INDEX);
                textFile[SwordConstants.TESTAMENT_OLD] = new File(otAllButLast + SUFFIX_TEXT);
                compFile[SwordConstants.TESTAMENT_OLD] = new File(otAllButLast + SUFFIX_COMP);

                String ntAllButLast = NetUtil.lengthenURI(path, File.separator + SwordConstants.FILE_NT + '.' + blockType.getIndicator() + SUFFIX_PART1)
                        .getPath();
                idxFile[SwordConstants.TESTAMENT_NEW] = new File(ntAllButLast + SUFFIX_INDEX);
                textFile[SwordConstants.TESTAMENT_NEW] = new File(ntAllButLast + SUFFIX_TEXT);
                compFile[SwordConstants.TESTAMENT_NEW] = new File(ntAllButLast + SUFFIX_COMP);
            }
        } catch (BookException e) {
            idxFile[SwordConstants.TESTAMENT_OLD] = null;
            textFile[SwordConstants.TESTAMENT_OLD] = null;
            compFile[SwordConstants.TESTAMENT_OLD] = null;

            idxFile[SwordConstants.TESTAMENT_NEW] = null;
            textFile[SwordConstants.TESTAMENT_NEW] = null;
            compFile[SwordConstants.TESTAMENT_NEW] = null;

            return;
        }

        if (idxFile[SwordConstants.TESTAMENT_OLD].canRead()) {
            try {
                idxRaf[SwordConstants.TESTAMENT_OLD] = new RandomAccessFile(idxFile[SwordConstants.TESTAMENT_OLD], FileUtil.MODE_READ);
                textRaf[SwordConstants.TESTAMENT_OLD] = new RandomAccessFile(textFile[SwordConstants.TESTAMENT_OLD], FileUtil.MODE_READ);
                compRaf[SwordConstants.TESTAMENT_OLD] = new RandomAccessFile(compFile[SwordConstants.TESTAMENT_OLD], FileUtil.MODE_READ);
            } catch (FileNotFoundException ex) {
                assert false : ex;
                log.error("Could not open OT", ex);
                idxRaf[SwordConstants.TESTAMENT_OLD] = null;
                textRaf[SwordConstants.TESTAMENT_OLD] = null;
                compRaf[SwordConstants.TESTAMENT_OLD] = null;
            }
        }

        if (idxFile[SwordConstants.TESTAMENT_NEW].canRead()) {
            try {
                idxRaf[SwordConstants.TESTAMENT_NEW] = new RandomAccessFile(idxFile[SwordConstants.TESTAMENT_NEW], FileUtil.MODE_READ);
                textRaf[SwordConstants.TESTAMENT_NEW] = new RandomAccessFile(textFile[SwordConstants.TESTAMENT_NEW], FileUtil.MODE_READ);
                compRaf[SwordConstants.TESTAMENT_NEW] = new RandomAccessFile(compFile[SwordConstants.TESTAMENT_NEW], FileUtil.MODE_READ);
            } catch (FileNotFoundException ex) {
                assert false : ex;
                log.error("Could not open NT", ex);
                idxRaf[SwordConstants.TESTAMENT_NEW] = null;
                textRaf[SwordConstants.TESTAMENT_NEW] = null;
                compRaf[SwordConstants.TESTAMENT_NEW] = null;
            }
        }

        active = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.common.activate.Activatable#deactivate(org.crosswire.common
     * .activate.Lock)
     */
    public final void deactivate(Lock lock) {
        if (idxRaf[SwordConstants.TESTAMENT_NEW] != null) {
            try {
                idxRaf[SwordConstants.TESTAMENT_NEW].close();
                textRaf[SwordConstants.TESTAMENT_NEW].close();
                compRaf[SwordConstants.TESTAMENT_NEW].close();
            } catch (IOException ex) {
                log.error("failed to close nt files", ex);
            } finally {
                idxRaf[SwordConstants.TESTAMENT_NEW] = null;
                textRaf[SwordConstants.TESTAMENT_NEW] = null;
                compRaf[SwordConstants.TESTAMENT_NEW] = null;
            }
        }

        if (idxRaf[SwordConstants.TESTAMENT_OLD] != null) {
            try {
                idxRaf[SwordConstants.TESTAMENT_OLD].close();
                textRaf[SwordConstants.TESTAMENT_OLD].close();
                compRaf[SwordConstants.TESTAMENT_OLD].close();
            } catch (IOException ex) {
                log.error("failed to close ot files", ex);
            } finally {
                idxRaf[SwordConstants.TESTAMENT_OLD] = null;
                textRaf[SwordConstants.TESTAMENT_OLD] = null;
                compRaf[SwordConstants.TESTAMENT_OLD] = null;
            }
        }

        active = false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.passage.Key#contains(org.crosswire.jsword.passage
     * .Key)
     */
    @Override
    public boolean contains(Key key) {
        checkActive();
        try {
            DataPolice.setKey(key);
            Verse verse = KeyUtil.getVerse(key);

            try {
                int testament = SwordConstants.getTestament(verse);
                long index = SwordConstants.getIndex(verse);

                // If Bible does not contain the desired testament, then false
                if (compRaf[testament] == null) {
                    return false;
                }

                // 10 because the index is 10 bytes long for each verse
                byte[] temp = SwordUtil.readRAF(compRaf[testament], index * COMP_ENTRY_SIZE, COMP_ENTRY_SIZE);

                // If the Bible does not contain the desired verse, return
                // nothing.
                // Some Bibles have different versification, so the requested
                // verse
                // may not exist.
                if (temp == null || temp.length == 0) {
                    return false;
                }

                // The data is little endian - extract the blockNum, verseStart
                // and
                // verseSize
                int verseSize = SwordUtil.decodeLittleEndian16(temp, 8);

                return verseSize > 0;

            } catch (IOException e) {
                return false;
            }
        } finally {
            DataPolice.setKey(null);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.sword.AbstractBackend#getRawText(org.crosswire
     * .jsword.passage.Key, java.lang.String)
     */
    @Override
    public String getRawText(Key key) throws BookException {
        checkActive();
        try {
            DataPolice.setKey(key);

            SwordBookMetaData sbmd = getBookMetaData();
            String charset = sbmd.getBookCharset();
            String compressType = (String) sbmd.getProperty(ConfigEntryType.COMPRESS_TYPE);

            Verse verse = KeyUtil.getVerse(key);

            try {
                int testament = SwordConstants.getTestament(verse);
                long index = SwordConstants.getIndex(verse);

                // If Bible does not contain the desired testament, return
                // nothing.
                if (compRaf[testament] == null) {
                    return "";
                }

                // 10 because the index is 10 bytes long for each verse
                byte[] temp = SwordUtil.readRAF(compRaf[testament], index * COMP_ENTRY_SIZE, COMP_ENTRY_SIZE);

                // If the Bible does not contain the desired verse, return
                // nothing.
                // Some Bibles have different versification, so the requested
                // verse
                // may not exist.
                if (temp == null || temp.length == 0) {
                    return "";
                }

                // The data is little endian - extract the blockNum, verseStart
                // and
                // verseSize
                long blockNum = SwordUtil.decodeLittleEndian32(temp, 0);
                int verseStart = SwordUtil.decodeLittleEndian32(temp, 4);
                int verseSize = SwordUtil.decodeLittleEndian16(temp, 8);

                //MJD start
                // do not return duplicate text for merged verses
                if (isPreventDuplicateVerseContent && index==lastIndex+1 && blockNum==lastBlockNum && verseStart==lastVerseStart && verseSize==lastVerseSize) {
                    lastIndex = index;
                    return "";
                }
                lastIndex = index;
                lastVerseStart = verseStart;
                lastVerseSize = verseSize;
                //MJD end

                // Can we get the data from the cache
                byte[] uncompressed = null;
                if (blockNum == lastBlockNum && testament == lastTestament) {
                    uncompressed = lastUncompressed;
                } else {
                    // Then seek using this index into the idx file
                    temp = SwordUtil.readRAF(idxRaf[testament], blockNum * IDX_ENTRY_SIZE, IDX_ENTRY_SIZE);
                    if (temp == null || temp.length == 0) {
                        return "";
                    }

                    int blockStart = SwordUtil.decodeLittleEndian32(temp, 0);
                    int blockSize = SwordUtil.decodeLittleEndian32(temp, 4);
                    int uncompressedSize = SwordUtil.decodeLittleEndian32(temp, 8);

                    // Read from the data file.
                    byte[] data = SwordUtil.readRAF(textRaf[testament], blockStart, blockSize);

                    decipher(data);

                    uncompressed = CompressorType.fromString(compressType).getCompressor(data).uncompress(uncompressedSize).toByteArray();

                    // cache the uncompressed data for next time
                    lastBlockNum = blockNum;
                    lastTestament = testament;
                    lastUncompressed = uncompressed;
                }

                // and cut out the required section.
                byte[] chopped = new byte[verseSize];
                System.arraycopy(uncompressed, verseStart, chopped, 0, verseSize);

                return SwordUtil.decode(key.getName(), chopped, charset);
            } catch (IOException e) {
                // TRANSLATOR: Common error condition: The file could not be read. There can be many reasons.
                // {0} is a placeholder for the file.
                throw new BookException(JSMsg.gettext("Error reading {0}", verse.getName()), e);
            }
        } finally {
            DataPolice.setKey(key);
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#setAliasKey(org.crosswire.jsword.passage.Key, org.crosswire.jsword.passage.Key)
     */
    @Override
    public void setAliasKey(Key alias, Key source) throws IOException {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#setRawText(org.crosswire.jsword.passage.Key, java.lang.String)
     */
    @Override
    public void setRawText(Key key, String text) throws BookException, IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Helper method so we can quickly activate ourselves on access
     */
    protected final void checkActive() {
        if (!active) {
            Activator.activate(this);
        }
    }

    /**
     * Whether the book is blocked by Book, Chapter or Verse.
     */
    private BlockType blockType;

    /**
     *
     */
    private int lastTestament = -1;

    private boolean isPreventDuplicateVerseContent;
    
    private long lastIndex = -1;
    private int lastVerseStart = -1;
    private int lastVerseSize = -1;
    /**
     *
     */
    private long lastBlockNum = -1;

    /**
     *
     */
    private byte[] lastUncompressed;

    /**
     * Are we active
     */
    private boolean active;

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(ZVerseBackend.class);

    /**
     * The array of index random access files
     */
    private RandomAccessFile[] idxRaf = new RandomAccessFile[3];

    /**
     * The array of data random access files
     */
    private RandomAccessFile[] textRaf = new RandomAccessFile[3];

    /**
     * The array of compressed random access files
     */
    private RandomAccessFile[] compRaf = new RandomAccessFile[3];

    /**
     * The array of index random access files
     */
    private File[] idxFile = new File[3];

    /**
     * The array of data random access files
     */
    private File[] textFile = new File[3];

    /**
     * The array of compressed random access files
     */
    private File[] compFile = new File[3];

    /**
     * How many bytes in the comp index?
     */
    private static final int COMP_ENTRY_SIZE = 10;

    /**
     * How many bytes in the idx index?
     */
    private static final int IDX_ENTRY_SIZE = 12;
}
