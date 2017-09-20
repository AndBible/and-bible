package net.bible.service.format;

import android.support.v4.util.Pools;

import net.bible.service.common.Logger;
import net.bible.service.common.ParseException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * SAXParser and SaxParserFactoey are not thread safe so cannot be used concurrently.
 * So instead of continually creating new parsers keep a pool.
 * My tests on my tablet with 5 windows only actually required 2 parsers and normally 1 is enough.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class SaxParserPool {

	private final Pools.SynchronizedPool<SAXParser> sPool = new Pools.SynchronizedPool<>(10);

	private final Logger log = new Logger("SaxParserPool");

	public SAXParser obtain() throws ParseException {
		SAXParser instance = sPool.acquire();
		if (instance!=null) {
			log.debug("Reusing SaxParser");
		}
		return (instance != null) ? instance : createSAXParser();
	}

	public void recycle(SAXParser parser) {
		if (parser!=null) {
			log.debug("Returning SaxParser");
			sPool.release(parser);
		}
	}

	private synchronized SAXParser createSAXParser() throws ParseException {
		log.debug("Creating SaxParser");
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setValidating(false);
			return spf.newSAXParser();
		} catch (Exception e) {
			log.error("SAX parser error", e);
			throw new ParseException("SAX parser error", e);
		}
	}
}
