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
 * ID: $Id: QueryBuilderFactory.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package org.crosswire.jsword.index.query;

import org.crosswire.common.util.Logger;
import org.crosswire.jsword.index.lucene.LuceneQueryBuilder;

/**
 * A Factory class for QueryBuilder.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public final class QueryBuilderFactory {
    /**
     * Prevent instantiation
     */
    private QueryBuilderFactory() {
    }

    /**
     * Create a new QueryBuilder.
     */
    public static QueryBuilder getQueryBuilder() {
        return instance;
    }

    /**
     * The singleton
     */
    private static QueryBuilder instance;

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(QueryBuilderFactory.class);

    /**
     * Setup the instance
     */
    static {
        //TODO MJD do we still need this
        try {
            instance = new LuceneQueryBuilder(); //(QueryBuilder) PluginUtil.getImplementation(QueryBuilder.class);
//        } catch (IOException e) {
//            log.error("create QueryBuilder failed", e); //$NON-NLS-1$
        } catch (ClassCastException e) {
            log.error("create QueryBuilder failed", e); //$NON-NLS-1$
//        } catch (ClassNotFoundException e) {
//            log.error("create QueryBuilder failed", e); //$NON-NLS-1$
//        } catch (InstantiationException e) {
//            log.error("create QueryBuilder failed", e); //$NON-NLS-1$
//        } catch (IllegalAccessException e) {
//            log.error("create QueryBuilder failed", e); //$NON-NLS-1$
        }
    }
}
