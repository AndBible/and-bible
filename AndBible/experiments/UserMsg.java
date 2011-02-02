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
 * ID: $Id: UserMsg.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package net.bible.service.sword;

import org.crosswire.common.util.MsgBase;

/**
 * Compile safe Msg resource settings.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
final class UserMsg extends MsgBase {
    static final UserMsg INDEX_START = new UserMsg("LuceneIndex.Indexing"); //$NON-NLS-1$
    static final UserMsg LUCENE_INIT = new UserMsg("LuceneIndex.LuceneInit"); //$NON-NLS-1$
    static final UserMsg SEARCH_FAILED = new UserMsg("LuceneIndex.SearchFailed"); //$NON-NLS-1$
    static final UserMsg OPTIMIZING = new UserMsg("LuceneIndex.Optimizing"); //$NON-NLS-1$
    static final UserMsg DELETE_FAILED = new UserMsg("LuceneIndex.DeleteFailed"); //$NON-NLS-1$
    static final UserMsg INSTALL_FAIL = new UserMsg("LuceneIndex.InstallFailed"); //$NON-NLS-1$
    static final UserMsg BAD_VERSE = new UserMsg("LuceneIndex.BadVerse"); //$NON-NLS-1$

    /**
     * Passthrough ctor
     */
    private UserMsg(String name) {
        super(name);
    }
}
