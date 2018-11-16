package net.bible.service.common;

/**
 * see http://www.crosswire.org/wiki/Frontends:URI_Standard
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
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
