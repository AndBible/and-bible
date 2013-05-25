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
 * Copyright: 2008
 *     The copyright to this program is held by it's authors.
 *
 */
package org.crosswire.jsword.book.sword;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.sword.state.OpenFileState;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.RestrictionType;

/**
 * A Backend that can be used as a global key list.
 * 
 * @param <T> The type of the OpenFileState that this class extends.
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith
 */
public abstract class AbstractKeyBackend<T extends OpenFileState> extends AbstractBackend<T> implements Key {
    /**
     * Simple ctor
     * 
     * @param sbmd
     *            the book's metadata
     */
    public AbstractKeyBackend(SwordBookMetaData sbmd) {
        super(sbmd);
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#canHaveChildren()
     */
    public boolean canHaveChildren() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getChildCount()
     */
    public int getChildCount() {
        return 0;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#isEmpty()
     */
    public boolean isEmpty() {
        return getCardinality() == 0;
    }

    @Override
    public boolean contains(Key key) {
        return indexOf(key) >= 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    public Iterator<Key> iterator() {
        return new Iterator<Key>() {

            /* (non-Javadoc)
             * @see java.util.Iterator#hasNext()
             */
            public boolean hasNext() {
                return here < count;
            }

            /* (non-Javadoc)
             * @see java.util.Iterator#next()
             */
            public Key next() throws NoSuchElementException {
                if (here >= count) {
                    throw new NoSuchElementException();
                }
                return get(here++);
            }

            /* (non-Javadoc)
             * @see java.util.Iterator#remove()
             */
            public void remove() {
                throw new UnsupportedOperationException();
            }

            private int here;
            private int count = getCardinality();
        };
    }


    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#addAll(org.crosswire.jsword.passage.Key)
     */
    public void addAll(Key key) {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#removeAll(org.crosswire.jsword.passage.Key)
     */
    public void removeAll(Key key) {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#clear()
     */
    public void clear() {
        throw new UnsupportedOperationException();
    }

    public void setAliasKey(T state, Key alias, Key source) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void setRawText(T state, Key key, String text) throws BookException, IOException {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getParent()
     */
    public Key getParent() {
        return null;
    }

    @Override
    public AbstractKeyBackend<T> clone() {
        AbstractKeyBackend<T> clone = null;
        try {
            clone = (AbstractKeyBackend<T>) super.clone();
        } catch (CloneNotSupportedException e) {
            assert false : e;
        }
        return clone;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getName()
     */
    public String getName() {
        return getBookMetaData().getInitials();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getName(org.crosswire.jsword.passage.Key)
     */
    public String getName(Key base) {
        return getName();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getOsisID()
     */
    public String getOsisID() {
        return getName();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getOsisRef()
     */
    public String getOsisRef() {
        return getName();
    }

   /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#getRootName()
     */
    public String getRootName() {
        return getName();
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#retainAll(org.crosswire.jsword.passage.Key)
     */
    public void retainAll(Key key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        // Since this can not be null
        if (obj == null) {
            return false;
        }

        // Check that that is the same as this
        // Don't use instanceOf since that breaks inheritance
        if (!obj.getClass().equals(this.getClass())) {
            return false;
        }

        return compareTo((Key) obj) == 0;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Key that) {

        if (this == that) {
            return 0;
        }

        if (that == null) {
            // he is empty, we are not so he is greater
            return -1;
        }

        int ret = this.getName().compareTo(that.getName());

        if (ret != 0) {
            return ret;
        }

        // Compare the contents.
        Iterator<Key> thisIter = this.iterator();
        Iterator<Key> thatIter = that.iterator();

        Key thisfirst = null;
        Key thatfirst = null;

        if (thisIter.hasNext()) {
            thisfirst = thisIter.next();
        }

        if (thatIter.hasNext()) {
            thatfirst = thatIter.next();
        }

        if (thisfirst == null) {
            if (thatfirst == null) {
                // we are both empty, and rank the same
                return 0;
            }
            // i am empty, he is not so we are greater
            return 1;
        }

        if (thatfirst == null) {
            // he is empty, we are not so he is greater
            return -1;
        }

        return thisfirst.getName().compareTo(thatfirst.getName());
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.passage.Key#blur(int, org.crosswire.jsword.passage.RestrictionType)
     */
    public void blur(int by, RestrictionType restrict) {
    }

    /**
     * Serialization ID
     */
    private static final long serialVersionUID = -2782112117361556089L;
}
