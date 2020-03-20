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

package net.bible.service.format;

import androidx.core.util.Pools;

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
