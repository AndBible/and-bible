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
 * ID: $Id: KeyUtil.java 2054 2010-12-10 22:12:09Z dmsmith $
 */
package org.crosswire.jsword.passage;

import org.crosswire.common.util.Logger;

/**
 * .
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public final class KeyUtil {
    /**
     * Prevent instantiation
     */
    private KeyUtil() {
    }

    /**
     * Walk through a tree visiting the nodes and branches in the tree
     * 
     * @param key
     *            The node tree to walk through
     * @param visitor
     *            The visitor to notify whenever a node is found
     */
    public static void visit(Key key, KeyVisitor visitor) {
        for (Key subkey : key) {
            if (subkey.canHaveChildren()) {
                visitor.visitBranch(subkey);
                visit(subkey, visitor);
            } else {
                visitor.visitLeaf(subkey);
            }
        }
    }

    /**
     * Not all keys represent verses, but we ought to be able to get something
     * close to a verse from anything that does verse like work.
     */
    public static Verse getVerse(Key key) {
        if (key instanceof Verse) {
            return (Verse) key;
        }

        if (key instanceof Passage) {
            Passage ref = getPassage(key);
            return ref.getVerseAt(0);
        }

        try {
        	//MJD prevent verse lookup errors in foreign languages see jsword e-mail from DM on 8.2.12 'False search hits with certain locales' 
            return VerseFactory.fromString(key.getOsisRef());
        } catch (NoSuchVerseException ex) {
            log.warn("Key can't be a verse: " + key.getOsisRef());
            return Verse.DEFAULT;
        }
    }

    /**
     * Not all keys represent passages, but we ought to be able to get something
     * close to a passage from anything that does passage like work. If you pass
     * a null key into this method, you get a null Passage out.
     */
    public static Passage getPassage(Key key) {
        if (key == null) {
            return null;
        }

        if (key instanceof Passage) {
            return (Passage) key;
        }

        Key ref = null;
        try {
        	//MJD prevent search errors in foreign languages see jsword e-mail from DM on 8.2.12 'False search hits with certain locales' 
            ref = keyf.getKey(key.getOsisRef());
        } catch (NoSuchKeyException ex) {
            log.warn("Key can't be a passage: " + key.getOsisRef());
            ref = keyf.createEmptyKeyList();
        }
        return (Passage) ref;
    }

    /**
     * How we create Passages
     */
    private static KeyFactory keyf = PassageKeyFactory.instance();

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(KeyUtil.class);
}
