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
 * ID: $Id: FilterFactory.java 1971 2009-12-04 03:05:54Z dmsmith $
 */
package org.crosswire.jsword.book.filter;

 import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.crosswire.common.util.Logger;
import org.crosswire.common.util.PluginUtil;
import org.crosswire.jsword.book.filter.osis.OSISFilter;

/**
 * A simple container for all the known filters.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public final class FilterFactory {
    /**
     * Prevent instantiation
     */
    private FilterFactory() {
    }

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(FilterFactory.class);

    /**
     * The lookup table of filters
     */
    private static Map filters = new HashMap();

    /**
     * The lookup table of filters
     */
    private static volatile Filter deft;

    /**
     * Populate the lookup table of filters and the default from the properties
     * file.
     */
    static {
        Map map = PluginUtil.getImplementorsMap(Filter.class);

        // the default value
        try {
            Class cdeft = (Class) map.remove("default"); //$NON-NLS-1$
            //MJD START
            System.out.println("Default filter:"+cdeft);
            if (cdeft!=null) {
            //MJD END
                deft = (Filter) cdeft.newInstance();
            //MJD START
            } else {
                deft = new OSISFilter();
            }
            //MJD END
        } catch (InstantiationException e) {
            log.fatal("Failed to get default filter, will attempt to use first", e); //$NON-NLS-1$
        } catch (IllegalAccessException e) {
            log.fatal("Failed to get default filter, will attempt to use first", e); //$NON-NLS-1$
        }

        // the lookup table
        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            try {
                Map.Entry entry = (Map.Entry) it.next();
                Class clazz = (Class) entry.getValue();
                Filter instance = (Filter) clazz.newInstance();
                addFilter((String) entry.getKey(), instance);
            } catch (InstantiationException ex) {
                log.error("Failed to add filter", ex); //$NON-NLS-1$
            } catch (IllegalAccessException ex) {
                log.error("Failed to add filter", ex); //$NON-NLS-1$
            }
        }

        // if the default didn't work then make a stab at an answer
        if (deft == null) {
            deft = (Filter) filters.values().iterator().next();
        }
    }

    /**
     * Find a filter given a lookup string. If lookup is null or the filter is
     * not found then the default filter will be used.
     */
    public static Filter getFilter(String lookup) {
        Filter reply = null;
        Iterator it = filters.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            if (key.equalsIgnoreCase(lookup)) {
                reply = (Filter) filters.get(key);
                break;
            }
        }

        if (reply == null) {
            reply = deft;
        }

        return (Filter) reply.clone();
    }

    /**
     * Find a filter given a lookup string
     */
    public static Filter getDefaultFilter() {
        return (Filter) deft.clone();
    }

    /**
     * Add to our list of known filters
     */
    public static void addFilter(String name, Filter instance) {
        filters.put(name, instance);
    }
}
