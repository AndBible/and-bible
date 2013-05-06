/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 or later
 * as published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005-2013
 *     The copyright to this program is held by it's authors.
 *
 */
package org.crosswire.jsword.book.basic;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.BookDriver;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.KeyType;
import org.crosswire.jsword.book.sword.MissingDataFilesException;
import org.crosswire.jsword.index.IndexStatus;
import org.jdom2.Document;

/**
 * An implementation of the Property Change methods from BookMetaData.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
/**
 *
 *
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith
 */
public abstract class AbstractBookMetaData implements BookMetaData {

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getKeyType()
     */
    public KeyType getKeyType() {
        return KeyType.LIST;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getDriver()
     */
    public BookDriver getDriver() {
        return driver;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getDriverName()
     */
    public String getDriverName() {
        if (getDriver() == null) {
            return null;
        }

        return getDriver().getDriverName();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#hasFeature(org.crosswire.jsword.book.FeatureType)
     */
    public boolean hasFeature(FeatureType feature) {
        return false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getOsisID()
     */
    public String getOsisID() {
        return getBookCategory().getName() + '.' + getInitials();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#isSupported()
     */
    public boolean isSupported() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#isEnciphered()
     */
    public boolean isEnciphered() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#isLocked()
     */
    public boolean isLocked() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#unlock(java.lang.String)
     */
    public boolean unlock(String unlockKey) {
        return false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getUnlockKey()
     */
    public String getUnlockKey() {
        return null;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#isQuestionable()
     */
    public boolean isQuestionable() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getLanguage()
     */
    public Language getLanguage() {
        return (Language) getProperty(KEY_XML_LANG);
    }

    /**
     * @param language
     *            The language to set.
     */
    public void setLanguage(Language language) {
        putProperty(KEY_XML_LANG, language);
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getLibrary()
     */
    public URI getLibrary() {
        URI uri = null;
        try {
            String loc = (String) getProperty(KEY_LIBRARY_URI);
            if (loc != null) {
                uri = new URI(loc);
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#setLibrary(java.net.URI)
     */
    public void setLibrary(URI library) throws MissingDataFilesException {
        putProperty(KEY_LIBRARY_URI, library.toString());
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#setLocation(java.net.URI)
     */
    public void setLocation(URI location) {
        putProperty(KEY_LOCATION_URI, location.toString());
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getLocation()
     */
    public URI getLocation() {
        URI uri = null;
        try {
            String loc = (String) getProperty(KEY_LOCATION_URI);
            if (loc != null) {
                uri = new URI(loc);
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getProperties()
     */
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(prop);
    }

    /**
     * @param newProperties
     */
    public void setProperties(Map<String, Object> newProperties) {
        prop = newProperties;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getProperty(java.lang.String)
     */
    public Object getProperty(String key) {
        return prop.get(key);
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#putProperty(java.lang.String, java.lang.Object)
     */
    public void putProperty(String key, Object value) {
        prop.put(key, value);
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#getIndexStatus()
     */
    public IndexStatus getIndexStatus() {
        return indexStatus;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#setIndexStatus(org.crosswire.jsword.index.IndexStatus)
     */
    public void setIndexStatus(IndexStatus newValue) {
        indexStatus = newValue;
        prop.put(KEY_INDEXSTATUS, newValue.toString());
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.BookMetaData#toOSIS()
     */
    public Document toOSIS() {
        throw new UnsupportedOperationException("If you want to use this, implement it.");
    }

    /**
     * @param driver
     *            The driver to set.
     */
    public void setDriver(BookDriver driver) {
        this.driver = driver;
    }

    @Override
    public boolean equals(Object obj) {
        // Since this can not be null
        if (obj == null) {
            return false;
        }

        // We might consider checking for equality against all BookMetaDatas?
        // However currently we don't.

        // Check that that is the same as this
        // Don't use instanceof since that breaks inheritance
        if (!obj.getClass().equals(this.getClass())) {
            return false;
        }

        // The real bit ...
        BookMetaData that = (BookMetaData) obj;

        return getBookCategory().equals(that.getBookCategory()) && getInitials().equals(that.getInitials()) && getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(BookMetaData obj) {
        int result = this.getBookCategory().compareTo(obj.getBookCategory());
        if (result == 0) {
            result = this.getInitials().compareTo(obj.getInitials());
        }
        if (result == 0) {
            result = this.getName().compareTo(obj.getName());
        }
        return result;
    }

    @Override
    public String toString() {
        return getInitials();
    }

    /**
     * The single key version of the properties
     */
    private Map<String, Object> prop = new LinkedHashMap<String, Object>();

    private BookDriver driver;
    private IndexStatus indexStatus = IndexStatus.UNDONE;
}
