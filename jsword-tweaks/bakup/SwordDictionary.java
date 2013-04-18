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
 * ID: $Id: SwordDictionary.java 2099 2011-03-07 17:13:00Z dmsmith $
 */
package org.crosswire.jsword.book.sword;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.crosswire.common.activate.Activator;
import org.crosswire.common.activate.Lock;
import org.crosswire.jsword.JSOtherMsg;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.book.basic.AbstractBook;
import org.crosswire.jsword.book.filter.Filter;
import org.crosswire.jsword.passage.DefaultKeyList;
import org.crosswire.jsword.passage.DefaultLeafKeyList;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.jdom.Content;
import org.jdom.Element;

/**
 * A Sword version of Dictionary.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class SwordDictionary extends AbstractBook {
    /**
     * Start and to as much checking as we can without using memory. (i.e.
     * actually reading the indexes)
     */
    protected SwordDictionary(SwordBookMetaData sbmd, AbstractBackend backend) {
        super(sbmd);

        this.backend = (AbstractKeyBackend) backend;
        this.filter = sbmd.getFilter();
        active = false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.Book#getOsisIterator(org.crosswire.jsword.passage
     * .Key, boolean)
     */
    public Iterator<Content> getOsisIterator(Key key, boolean allowEmpty) throws BookException {
        checkActive();

        assert key != null;
        assert backend != null;

        List<Content> content = new ArrayList<Content>();
        Element title = OSISUtil.factory().createTitle();
        // this attribute marks the header as being automatically added by JSword
        title.setAttribute(OSISUtil.OSIS_ATTR_TYPE, OSISUtil.GENERATED_CONTENT);
        title.addContent(key.getName());
        content.add(title);

        String txt = backend.getRawText(key);

        List<Content> osisContent = filter.toOSIS(this, key, txt);
        content.addAll(osisContent);

        return content.iterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.Book#contains(org.crosswire.jsword.passage.Key)
     */
    public boolean contains(Key key) {
        return backend != null && backend.contains(key);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.Book#getRawText(org.crosswire.jsword.passage
     * .Key)
     */
    public String getRawText(Key key) throws BookException {
        checkActive();

        assert key != null;
        assert backend != null;

        return backend.getRawText(key);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.Book#isWritable()
     */
    public boolean isWritable() {
        return backend.isWritable();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.basic.AbstractPassageBook#setRawText(org.crosswire
     * .jsword.passage.Key, java.lang.String)
     */
    public void setRawText(Key key, String rawData) throws BookException {
        throw new BookException(JSOtherMsg.lookupText("This Book is read-only."));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.Book#setAliasKey(org.crosswire.jsword.passage
     * .Key, org.crosswire.jsword.passage.Key)
     */
    public void setAliasKey(Key alias, Key source) throws BookException {
        throw new BookException(JSOtherMsg.lookupText("This Book is read-only."));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.passage.KeyFactory#getGlobalKeyList()
     */
    public Key getGlobalKeyList() {
        checkActive();

        return backend;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.passage.KeyFactory#isValidKey(java.lang.String)
     */
    public Key getValidKey(String name) {
        try {
            return getKey(name);
        } catch (NoSuchKeyException e) {
            return createEmptyKeyList();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.passage.KeyFactory#getKey(java.lang.String)
     */
    public Key getKey(String text) throws NoSuchKeyException {
        checkActive();

        int pos = backend.indexOf(new DefaultLeafKeyList(text));
        if (pos < 0) {
            if (backend.getCardinality() > -pos - 1) {
                return backend.get(-pos - 1);
            }
            return backend.get(backend.getCardinality() - 1);
        }
        return backend.get(pos);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.passage.KeyFactory#getEmptyKeyList()
     */
    public Key createEmptyKeyList() {
        return new DefaultKeyList();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.common.activate.Activatable#activate(org.crosswire.common
     * .activate.Lock)
     */
    @Override
    public final void activate(Lock lock) {
        super.activate(lock);
        active = true;

        // We don't need to activate the backend because it should be capable
        // of doing it for itself.
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.common.activate.Activatable#deactivate(org.crosswire.common
     * .activate.Lock)
     */
    @Override
    public final void deactivate(Lock lock) {
        super.deactivate(lock);
        Activator.deactivate(backend);
        active = false;
    }

    /**
     * Helper method so we can quickly activate ourselves on access
     */
    private void checkActive() {
        if (!active) {
            Activator.activate(this);
        }
    }

    /**
     * Are we active
     */
    private boolean active;

    /**
     * To read the data from the disk
     */
    private AbstractKeyBackend backend;

    /**
     * The filter to use to convert to OSIS.
     */
    private Filter filter;
}
