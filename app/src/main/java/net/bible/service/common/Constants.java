/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.service.common;

/**
 * see http://www.crosswire.org/wiki/Frontends:URI_Standard
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Constants {
    // Strings for URL protocols/URI schemes
    public static final String SWORD_PROTOCOL = "sword"; //$NON-NLS-1$
    public static final String BIBLE_PROTOCOL = "bible"; //$NON-NLS-1$
    public static final String DICTIONARY_PROTOCOL = "dict"; //$NON-NLS-1$
    public static final String GREEK_DEF_PROTOCOL = "gdef"; //$NON-NLS-1$
    public static final String HEBREW_DEF_PROTOCOL = "hdef"; //$NON-NLS-1$
    public static final String ALL_GREEK_OCCURRENCES_PROTOCOL = "allgoccur"; //$NON-NLS-1$
    public static final String ALL_HEBREW_OCCURRENCES_PROTOCOL = "allhoccur"; //$NON-NLS-1$
    public static final String ROBINSON_GREEK_MORPH_PROTOCOL = "robinson"; //$NON-NLS-1$
    public static final String HEBREW_MORPH_PROTOCOL = "hmorph"; //$NON-NLS-1$
    public static final String COMMENTARY_PROTOCOL = "comment"; //$NON-NLS-1$

    public static class HTML {
    	public static final String NBSP = "&#160;";
    	public static final String SPACE = " ";
    	public static final String BR = "<br />";
    }  
}
