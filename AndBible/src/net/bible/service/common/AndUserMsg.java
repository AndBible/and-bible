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
 * ID: $Id: MsgBase.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package net.bible.service.common;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import net.bible.service.sword.Logger;

import org.crosswire.common.icu.NumberShaper;
import org.crosswire.common.util.CWClassLoader;
import org.crosswire.common.util.MsgBase;

/**
 * A base class for implementing type safe internationalization (i18n) that is
 * easy for most cases. See {@link org.crosswire.common.util.Msg} for an example
 * of how to inherit from here.
 * 
 * <p>
 * Some Regex/Vi macros to convert from a half way house i18n scheme where the
 * strings are in Msg classes but not properties files: The following makes the
 * lookup string simple :%s/Msg \([^ ]*\) = new Msg(".*")/Msg \1 = new
 * Msg("\1")/ These turn a lookup string into a properties file :%s/ static
 * final Msg // :%s/ = new Msg("/: / :%s/");\/\/\$NON-NLS-1\$$/
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author DM Smith [dmsmith555 at yahoo dot com]
 * @see org.crosswire.common.util.Msg
 */
public class AndUserMsg extends MsgBase {
    /**
     * Create a MsgBase object
     */
    protected AndUserMsg(String msg) {
    	super(msg);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.lang.enum.Enum#toString()
     */
    /* @Override */
    public String toString() {
        return name;
    }



    private String obtainString() {
        try {
            if (resources != null) {
                return resources.getString(name);
            }
        } catch (MissingResourceException ex) {
            log.error("Missing resource: Locale=" + Locale.getDefault().toString() + " name=" + name + " package=" + getClass().getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        return name;
    }

    private String name;

    /**
     * resource map maintains a mapping of class names to resources found by
     * that name.
     */
    private static Map resourceMap = new HashMap();

    /**
     * If there is any internationalization to be done, it is thru this
     */
    private ResourceBundle resources;

    /** Internationalize numbers */
    private NumberShaper shaper;

    /**
     * The log stream
     */
    private static final Logger log = new Logger("AndUserMsg");
}
