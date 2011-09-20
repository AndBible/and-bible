package org.crosswire.jsword.book.sword;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.URI;

import org.crosswire.common.activate.Activator;
import org.crosswire.common.activate.Lock;
import org.crosswire.common.compress.CompressorType;
import org.crosswire.common.util.FileUtil;
import org.crosswire.common.util.NetUtil;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.DataPolice;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.xml.sax.InputSource;

public class ZVerseBackendLite extends AbstractBackend {
    private File[] idxFile = new File[3];
    private File[] textFile = new File[3];
    private File[] compFile = new File[3];
    private RandomAccessFile[] idxRaf = new RandomAccessFile[3];
    private RandomAccessFile[] textRaf = new RandomAccessFile[3];
    private RandomAccessFile[] compRaf = new RandomAccessFile[3];

    private static final String SUFFIX_COMP = "v"; //$NON-NLS-1$
    private static final String SUFFIX_INDEX = "s"; //$NON-NLS-1$
    private static final String SUFFIX_PART1 = "z"; //$NON-NLS-1$
    private static final String SUFFIX_TEXT = "z"; //$NON-NLS-1$

    private static final int COMP_ENTRY_SIZE = 10;
    private static final int IDX_ENTRY_SIZE = 12;

    private int lastTestament = -1;
    private long lastBlockNum = -1;
    private byte[] lastUncompressed;
    
    private BlockType blockType;
    
    private static final Logger log = new Logger(ZVerseBackendLite.class.getName());

    /**
     * Are we active
     */
    private boolean active;

    private static final String TAG = "SwordBookReaderLite";
    
    public ZVerseBackendLite(SwordBookMetaData sbmd) {
        super(sbmd);
        blockType = BlockType.fromString((String) sbmd.getProperty(ConfigEntryType.BLOCK_TYPE));
    }
    
    public Element getOsis(Key key, SwordBookMetaData sbmd) throws BookException {
        try {
            String plain = getRawText(key);
            // create a root element to house our document fragment
            StringReader in = new StringReader("<div>" + plain + "</div>"); //$NON-NLS-1$ //$NON-NLS-2$
            InputSource is = new InputSource(in);
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(is);
            Element div = doc.getRootElement();
    
            return div;
        } catch (Exception e) {
            throw new BookException(UserMsg.READ_FAIL, e, new Object[] {
                    key.getName()
                });
        }
    }
    
    
    public String getTextDoc(Key key) throws BookException {
    	return "<div>"+getRawText(key)+"</div>";
    }

    public String getRawText(Key key) throws BookException {
    	checkActive();
        try {
            DataPolice.setKey(key);

            String charset = getBookMetaData().getBookCharset();
            String compressType = (String) getBookMetaData().getProperty(ConfigEntryType.COMPRESS_TYPE);

            Verse verse = KeyUtil.getVerse(key);

            int testament = SwordConstants.getTestament(verse);
            long index = SwordConstants.getIndex(verse);

            // If Bible does not contain the desired testament, return
            // nothing.
            if (compRaf[testament] == null) {
                return ""; //$NON-NLS-1$
            }

            // 10 because the index is 10 bytes long for each verse
            byte[] temp = SwordUtil.readRAF(compRaf[testament], index * COMP_ENTRY_SIZE, COMP_ENTRY_SIZE);

            // If the Bible does not contain the desired verse, return
            // nothing.
            // Some Bibles have different versification, so the requested
            // verse
            // may not exist.
            if (temp == null || temp.length == 0) {
                return ""; //$NON-NLS-1$
            }

            // The data is little endian - extract the blockNum, verseStart
            // and
            // verseSize
            long blockNum = SwordUtil.decodeLittleEndian32(temp, 0);
            int verseStart = SwordUtil.decodeLittleEndian32(temp, 4);
            int verseSize = SwordUtil.decodeLittleEndian16(temp, 8);

            // Can we get the data from the cache
            byte[] uncompressed = null;
            if (blockNum == lastBlockNum && testament == lastTestament) {
                uncompressed = lastUncompressed;
            } else {
                // Then seek using this index into the idx file
                temp = SwordUtil.readRAF(idxRaf[testament], blockNum * IDX_ENTRY_SIZE, IDX_ENTRY_SIZE);
                if (temp == null || temp.length == 0) {
                    return ""; //$NON-NLS-1$
                }

                int blockStart = SwordUtil.decodeLittleEndian32(temp, 0);
                int blockSize = SwordUtil.decodeLittleEndian32(temp, 4);
                int uncompressedSize = SwordUtil.decodeLittleEndian32(temp, 8);

                // Read from the data file.
                byte[] data = SwordUtil.readRAF(textRaf[testament], blockStart, blockSize);

                // decipher(data);

                uncompressed = CompressorType.fromString(compressType).getCompressor(data).uncompress(uncompressedSize).toByteArray();

                // cache the uncompressed data for next time
                lastBlockNum = blockNum;
                lastTestament = testament;
                lastUncompressed = uncompressed;
            }

            // and cut out the required section.
            byte[] chopped = new byte[verseSize];
            System.arraycopy(uncompressed, verseStart, chopped, 0, verseSize);

            return SwordUtil.decode(key.getName(), uncompressed, charset);
        } catch (Exception e) {
            throw new BookException(UserMsg.READ_FAIL, e, new Object[] {
                key.getName()
            });
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#setAliasKey(org.crosswire.jsword.passage.Key, org.crosswire.jsword.passage.Key)
     */
    public void setAliasKey(Key alias, Key source) throws IOException {
        throw new UnsupportedOperationException();
    }
    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#setRawText(org.crosswire.jsword.passage.Key, java.lang.String)
     */
    public void setRawText(Key key, String text) throws BookException, IOException {
        throw new UnsupportedOperationException();
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
                log.error("Could not open OT", ex); //$NON-NLS-1$
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
                log.error("Could not open NT", ex); //$NON-NLS-1$
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
                log.error("failed to close nt files", ex); //$NON-NLS-1$
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
                log.error("failed to close ot files", ex); //$NON-NLS-1$
            } finally {
                idxRaf[SwordConstants.TESTAMENT_OLD] = null;
                textRaf[SwordConstants.TESTAMENT_OLD] = null;
                compRaf[SwordConstants.TESTAMENT_OLD] = null;
            }
        }

        active = false;
    }

    /**
     * Helper method so we can quickly activate ourselves on access
     */
    protected final void checkActive() {
        if (!active) {
            Activator.activate(this);
        }
    }

	@Override
	public boolean contains(Key key) {
		// TODO Auto-generated method stub
		return false;
	}
}
