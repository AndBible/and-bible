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
 * ID: $Id: ConfigEntryTable.java 2068 2011-01-01 23:53:21Z dmsmith $
 */
package org.crosswire.jsword.book.sword;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.crosswire.common.util.Language;
import org.crosswire.common.util.Languages;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.OSISUtil;
import org.jdom.Element;

/**
 * A utility class for loading the entries in a Sword book's conf file. Since
 * the conf files are manually maintained, there can be all sorts of errors in
 * them. This class does robust checking and reporting.
 * 
 * <p>
 * Config file format. See also: <a href=
 * "http://sword.sourceforge.net/cgi-bin/twiki/view/Swordapi/ConfFileLayout">
 * http://sword.sourceforge.net/cgi-bin/twiki/view/Swordapi/ConfFileLayout</a>
 * 
 * <p>
 * The contents of the About field are in rtf.
 * <p>
 * \ is used as a continuation line.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Mark Goodwin [mark at thorubio dot org]
 * @author Joe Walker [joe at eireneh dot com]
 * @author Jacky Cheung
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public final class ConfigEntryTable {
    /**
     * Create an empty Sword config for the named book.
     * 
     * @param bookName
     *            the name of the book
     */
    public ConfigEntryTable(String bookName) {
        table = new HashMap<ConfigEntryType,ConfigEntry>();
        extra = new TreeMap<String,ConfigEntry>();
        internal = bookName;
        supported = true;
    }

    private static long MAX_BUFF_SIZE = 8*1024;
    private static int MIN_BUFF_SIZE = 128;
    
    /**
     * Load the conf from a file.
     * 
     * @param file
     *            the file to load
     * @throws IOException
     */
    public void load(File file) throws IOException {
        configFile = file;

        BufferedReader in = null;
        try {
        	//MJD start get best buffersize but ensure it is not too small (0) nor too large (>default)
            int bufferSize = (int)Math.min(MAX_BUFF_SIZE, file.length());
            bufferSize = Math.max(MIN_BUFF_SIZE, bufferSize);
            
            // Quiet Android from complaining about using the default BufferReader buffer size.
            // The actual buffer size is undocumented. So this is a good idea any way.
            in = new BufferedReader(new InputStreamReader(new FileInputStream(file), ENCODING_UTF8), bufferSize);
            //MJD end
            loadInitials(in);
            loadContents(in);
            in.close();
            in = null;
            if (getValue(ConfigEntryType.ENCODING).equals(ENCODING_LATIN1)) {
                supported = true;
                bookType = null;
                questionable = false;
                readahead = null;
                table.clear();
                extra.clear();
                in = new BufferedReader(new InputStreamReader(new FileInputStream(file), ENCODING_LATIN1), bufferSize);
                loadInitials(in);
                loadContents(in);
                in.close();
                in = null;
            }
            adjustDataPath();
            adjustLanguage();
            adjustBookType();
            adjustName();
            validate();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Load the conf from a buffer. This is used to load conf entries from the
     * mods.d.tar.gz file.
     * 
     * @param buffer
     *            the buffer to load
     * @throws IOException
     */
    public void load(byte[] buffer) throws IOException {
        BufferedReader in = null;
        try {
            // Quiet Android from complaining about using the default BufferReader buffer size.
            // The actual buffer size is undocumented. So this is a good idea any way.
            in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer), ENCODING_UTF8), buffer.length);
            loadInitials(in);
            loadContents(in);
            in.close();
            in = null;
            if (getValue(ConfigEntryType.ENCODING).equals(ENCODING_LATIN1)) {
                supported = true;
                bookType = null;
                questionable = false;
                readahead = null;
                table.clear();
                extra.clear();
                in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer), ENCODING_LATIN1), buffer.length);
                loadInitials(in);
                loadContents(in);
                in.close();
                in = null;
            }
            adjustDataPath();
            adjustLanguage();
            adjustBookType();
            adjustName();
            validate();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Determines whether the Sword Book's conf is supported by JSword.
     */
    public boolean isQuestionable() {
        return questionable;
    }

    /**
     * Determines whether the Sword Book's conf is supported by JSword.
     */
    public boolean isSupported() {
        return supported;
    }

    /**
     * Determines whether the Sword Book is enciphered.
     * 
     * @return true if enciphered
     */
    public boolean isEnciphered() {
        String cipher = (String) getValue(ConfigEntryType.CIPHER_KEY);
        return cipher != null;
    }

    /**
     * Determines whether the Sword Book is enciphered and without a key.
     * 
     * @return true if enciphered
     */
    public boolean isLocked() {
        String cipher = (String) getValue(ConfigEntryType.CIPHER_KEY);
        return cipher != null && cipher.length() == 0;
    }

    /**
     * Unlocks a book with the given key. The key is trimmed of any leading or
     * trailing whitespace.
     * 
     * @param unlockKey
     *            the key to try
     * @return true if the unlock key worked.
     */
    public boolean unlock(String unlockKey) {
        String tmpKey = unlockKey;
        if (tmpKey != null) {
            tmpKey = tmpKey.trim();
        }
        add(ConfigEntryType.CIPHER_KEY, tmpKey);
        if (configFile != null) {
            try {
                save();
            } catch (IOException e) {
                // TRANSLATOR: Common error condition: The user supplied unlock key could not be saved.
                Reporter.informUser(this, UserMsg.gettext("Unable to save the book's unlock key."));
            }
        }
        return true;
    }

    /**
     * Gets the unlock key for the module.
     * 
     * @return the unlock key, if any, null otherwise.
     */
    public String getUnlockKey() {
        return (String) getValue(ConfigEntryType.CIPHER_KEY);
    }

    /**
     * Returns an Enumeration of all the known keys found in the config file.
     */
    public Set<ConfigEntryType> getKeys() {
        return table.keySet();
    }

    /**
     * Returns an Enumeration of all the unknown keys found in the config file.
     */
    public Set<String> getExtraKeys() {
        return extra.keySet();
    }

    /**
     * Returns an Enumeration of all the keys found in the config file.
     */
    public BookType getBookType() {
        return bookType;
    }

    /**
     * Gets a particular ConfigEntry's value by its type
     * 
     * @param type
     *            of the ConfigEntry
     * @return the requested value, the default (if there is no entry) or null
     *         (if there is no default)
     */
    public Object getValue(ConfigEntryType type) {
        ConfigEntry ce = table.get(type);
        if (ce != null) {
            return ce.getValue();
        }
        return type.getDefault();
    }

    /**
     * Determine whether this ConfigEntryTable has the ConfigEntry and it
     * matches the value.
     * 
     * @param type
     *            The kind of ConfigEntry to look for
     * @param search
     *            the value to match against
     * @return true if there is a matching ConfigEntry matching the value
     */
    public boolean match(ConfigEntryType type, Object search) {
        ConfigEntry ce = table.get(type);
        return ce != null && ce.match(search);
    }

    /**
     * Gets a particular unknown entries value by its key
     * 
     * @param key
     *            of the unknown entry
     * @return the requested value or null (if there is no value)
     */
    public Object getExtraValue(String key) {
        ConfigEntry ce = table.get(key);
        if (ce != null) {
            return ce.getValue();
        }
        return null;
    }

    /**
     * Determine whether this ConfigEntryTable has the ConfigEntry and it
     * matches the value.
     * 
     * @param key
     *            The kind of unknown entry to look for
     * @param search
     *            the value to match against
     * @return true if there is a unknown entry matching the value
     */
    public boolean matchExtra(String key, String search) {
        ConfigEntry ce = table.get(key);
        return ce != null && ce.match(search);
    }

    /**
     * Sort the keys for a more meaningful presentation order.
     */
    public Element toOSIS() {
        OSISUtil.OSISFactory factory = OSISUtil.factory();
        Element ele = factory.createTable();
        toOSIS(factory, ele, "BasicInfo", BASIC_INFO);
        toOSIS(factory, ele, "LangInfo", LANG_INFO);
        toOSIS(factory, ele, "LicenseInfo", COPYRIGHT_INFO);
        toOSIS(factory, ele, "FeatureInfo", FEATURE_INFO);
        toOSIS(factory, ele, "SysInfo", SYSTEM_INFO);
        toOSIS(factory, ele, "Extra", extra);
        return ele;
    }

    /**
     * Build's a SWORD conf file as a string. The result is not identical to the
     * original, cleaning up problems in the original and re-arranging the
     * entries into a predictable order.
     * 
     * @return the well-formed conf.
     */
    public String toConf() {
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        buf.append(getValue(ConfigEntryType.INITIALS));
        buf.append("]\n");
        toConf(buf, BASIC_INFO);
        toConf(buf, SYSTEM_INFO);
        toConf(buf, HIDDEN);
        toConf(buf, FEATURE_INFO);
        toConf(buf, LANG_INFO);
        toConf(buf, COPYRIGHT_INFO);
        toConf(buf, extra);
        return buf.toString();
    }

    public void save() throws IOException {
        if (configFile != null) {
            // The encoding of the conf must match the encoding of the module.
            String encoding = ENCODING_LATIN1;
            if (getValue(ConfigEntryType.ENCODING).equals(ENCODING_UTF8)) {
                encoding = ENCODING_UTF8;
            }
            Writer writer = null;
            try {
                writer = new OutputStreamWriter(new FileOutputStream(configFile), encoding);
                writer.write(toConf());
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    public void save(File file) throws IOException {
        this.configFile = file;
        this.save();
    }

    private void loadContents(BufferedReader in) throws IOException {
        StringBuilder buf = new StringBuilder();
        while (true) {
            // Empty out the buffer
            buf.setLength(0);

            String line = advance(in);
            if (line == null) {
                break;
            }

            // skip blank lines
            if (line.length() == 0) {
                continue;
            }

            Matcher matcher = KEY_VALUE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                log.warn("Expected to see '=' in " + internal + ": " + line);
                continue;
            }

            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            // Only CIPHER_KEYS that are empty are not ignored
            if (value.length() == 0 && !ConfigEntryType.CIPHER_KEY.getName().equals(key)) {
                log.warn("Ignoring empty entry in " + internal + ": " + line);
                continue;
            }

            // Create a configEntry so that the name is normalized.
            ConfigEntry configEntry = new ConfigEntry(internal, key);

            ConfigEntryType type = configEntry.getType();

            ConfigEntry e = table.get(type);

            if (e == null) {
                if (type == null) {
                    log.warn("Extra entry in " + internal + " of " + configEntry.getName());
                    extra.put(key, configEntry);
                } else if (type.isSynthetic()) {
                    log.warn("Ignoring unexpected entry in " + internal + " of " + configEntry.getName());
                } else {
                    table.put(type, configEntry);
                }
            } else {
                configEntry = e;
            }

            buf.append(value);
            getContinuation(configEntry, in, buf);

            // History is a special case it is of the form History_x.x
            // The config entry is History without the x.x.
            // We want to put x.x at the beginning of the string
            value = buf.toString();
            if (ConfigEntryType.HISTORY.equals(type)) {
                int pos = key.indexOf('_');
                value = key.substring(pos + 1) + ' ' + value;
            }

            configEntry.addValue(value);
        }
    }

    private void loadInitials(BufferedReader in) throws IOException {
        String initials = null;
        while (true) {
            String line = advance(in);
            if (line == null) {
                break;
            }

            if (line.charAt(0) == '[' && line.charAt(line.length() - 1) == ']') {
                // The conf file contains a leading line of the form [KJV]
                // This is the acronym by which Sword refers to it.
                initials = line.substring(1, line.length() - 1);
                break;
            }
        }
        if (initials == null) {
            log.error("Malformed conf file for " + internal + " no initials found. Using internal of " + internal);
            initials = internal;
        }
        add(ConfigEntryType.INITIALS, initials);
    }

    /**
     * Get continuation lines, if any.
     */
    private void getContinuation(ConfigEntry configEntry, BufferedReader bin, StringBuilder buf) throws IOException {
        for (String line = advance(bin); line != null; line = advance(bin)) {
            int length = buf.length();

            // Look for bad data as this condition did exist
            boolean continuation_expected = length > 0 && buf.charAt(length - 1) == '\\';

            if (continuation_expected) {
                // delete the continuation character
                buf.deleteCharAt(length - 1);
            }

            if (isKeyLine(line)) {
                if (continuation_expected) {
                    log.warn(report("Continuation followed by key for", configEntry.getName(), line));
                }

                backup(line);
                break;
            } else if (!continuation_expected) {
                log.warn(report("Line without previous continuation for", configEntry.getName(), line));
            }

            if (!configEntry.allowsContinuation()) {
                log.warn(report("Ignoring unexpected additional line for", configEntry.getName(), line));
            } else {
                if (continuation_expected) {
                    buf.append('\n');
                }
                buf.append(line);
            }
        }
    }

    /**
     * Get the next line from the input
     * 
     * @param bin
     *            The reader to get data from
     * @return the next line
     * @throws IOException
     */
    private String advance(BufferedReader bin) throws IOException {
        // Was something put back? If so, return it.
        if (readahead != null) {
            String line = readahead;
            readahead = null;
            return line;
        }

        // Get the next non-blank, non-comment line
        String trimmed = null;
        for (String line = bin.readLine(); line != null; line = bin.readLine()) {
            // Remove trailing whitespace
            trimmed = line.trim();

            int length = trimmed.length();

            // skip blank and comment lines
            if (length != 0 && trimmed.charAt(0) != '#') {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Read too far ahead and need to return a line.
     */
    private void backup(String oops) {
        if (oops.length() > 0) {
            readahead = oops;
        } else {
            // should never happen
            log.error("Backup an empty string for " + internal);
        }
    }

    /**
     * Does this line of text represent a key/value pair?
     */
    private boolean isKeyLine(String line) {
        return KEY_VALUE_PATTERN.matcher(line).matches();
    }

    /**
     * A helper to create/replace a value for a given type.
     * 
     * @param type
     * @param aValue
     */
    public void add(ConfigEntryType type, String aValue) {
        table.put(type, new ConfigEntry(internal, type, aValue));
    }

    private void adjustDataPath() {
        String datapath = (String) getValue(ConfigEntryType.DATA_PATH);
        if (datapath == null) {
            datapath = "";
        }
        if (datapath.startsWith("./")) {
            datapath = datapath.substring(2);
        }
        add(ConfigEntryType.DATA_PATH, datapath);
    }

    private void adjustLanguage() {
        Language lang = (Language) getValue(ConfigEntryType.LANG);
        if (lang == null) {
            lang = Language.DEFAULT_LANG;
            add(ConfigEntryType.LANG, lang.toString());
        }
        testLanguage(internal, lang);

        Language langFrom = (Language) getValue(ConfigEntryType.GLOSSARY_FROM);
        Language langTo = (Language) getValue(ConfigEntryType.GLOSSARY_TO);

        // If we have either langFrom or langTo, we are dealing with a glossary
        if (langFrom != null || langTo != null) {
            if (langFrom == null) {
                log.warn("Missing data for " + internal + ". Assuming " + ConfigEntryType.GLOSSARY_FROM.getName() + '=' + Languages.DEFAULT_LANG_CODE);
                langFrom = Language.DEFAULT_LANG;
                add(ConfigEntryType.GLOSSARY_FROM, lang.getCode());
            }
            testLanguage(internal, langFrom);

            if (langTo == null) {
                log.warn("Missing data for " + internal + ". Assuming " + ConfigEntryType.GLOSSARY_TO.getName() + '=' + Languages.DEFAULT_LANG_CODE);
                langTo = Language.DEFAULT_LANG;
                add(ConfigEntryType.GLOSSARY_TO, lang.getCode());
            }
            testLanguage(internal, langTo);

            // At least one of the two languages should match the lang entry
            if (!langFrom.equals(lang) && !langTo.equals(lang)) {
                log.error("Data error in " + internal
                          + ". Neither " + ConfigEntryType.GLOSSARY_FROM.getName()
                          + " or " + ConfigEntryType.GLOSSARY_FROM.getName()
                          + " match " + ConfigEntryType.LANG.getName());
            } else if (!langFrom.equals(lang)) {
                // The LANG field should match the GLOSSARY_FROM field
                /*
                 * log.error("Data error in " + internal + ". " +
                 * ConfigEntryType.GLOSSARY_FROM.getName() + " ("
                 * + langFrom.getCode() + ") does not match " +
                 * ConfigEntryType.LANG.getName() + " (" + lang.getCode() +
                 * ")");
                 */
                lang = langFrom;
                add(ConfigEntryType.LANG, lang.getCode());
            }
        }
    }

    private void adjustBookType() {
        // The book type represents the underlying category of book.
        // Fine tune it here.
        BookCategory focusedCategory = (BookCategory) getValue(ConfigEntryType.CATEGORY);
        questionable = focusedCategory == BookCategory.QUESTIONABLE;

        // From the config map, extract the important bean properties
        String modTypeName = (String) getValue(ConfigEntryType.MOD_DRV);
        if (modTypeName == null) {
            log.error("Book not supported: malformed conf file for " + internal + " no " + ConfigEntryType.MOD_DRV.getName() + " found");
            supported = false;
            return;
        }

        bookType = BookType.fromString(modTypeName);
        if (getBookType() == null) {
            log.error("Book not supported: malformed conf file for " + internal + " no book type found");
            supported = false;
            return;
        }

        BookCategory basicCategory = getBookType().getBookCategory();
        if (basicCategory == null) {
            supported = false;
            return;
        }

        // The book type represents the underlying category of book.
        // Fine tune it here.
        if (focusedCategory == BookCategory.OTHER || focusedCategory == BookCategory.QUESTIONABLE) {
            focusedCategory = getBookType().getBookCategory();
        }

        add(ConfigEntryType.CATEGORY, focusedCategory.getName());
    }

    private void adjustName() {
        // If there is no name then use the internal name
        if (table.get(ConfigEntryType.DESCRIPTION) == null) {
            log.error("Malformed conf file for " + internal + " no " + ConfigEntryType.DESCRIPTION.getName() + " found. Using internal of " + internal);
            add(ConfigEntryType.DESCRIPTION, internal);
        }
    }

    /**
     * Determine which books are not supported. Also, report on problems.
     */
    private void validate() {
        // if (isEnciphered())
        // {
        //            log.debug("Book not supported: " + internal + " because it is locked and there is no key.");
        // supported = false;
        // return;
        // }
    }

    private void testLanguage(String initials, Language lang) {
        if (!lang.isValidLanguage()) {
            log.warn("Unknown language " + lang.getCode() + " in book " + initials);
        }
    }

    /**
     * Build an ordered map so that it displays in a consistent order.
     */
    private void toOSIS(OSISUtil.OSISFactory factory, Element ele, String aTitle, ConfigEntryType[] category) {
        Element title = null;
        for (int i = 0; i < category.length; i++) {
            ConfigEntry entry = table.get(category[i]);
            Element configElement = null;

            if (entry != null) {
                configElement = entry.toOSIS();
            }

            if (title == null && configElement != null) {
                // I18N(DMS): use aTitle to lookup translation.
                title = factory.createHeader();
                title.addContent(aTitle);
                ele.addContent(title);
            }

            if (configElement != null) {
                ele.addContent(configElement);
            }
        }
    }

    private void toConf(StringBuilder buf, ConfigEntryType[] category) {
        for (int i = 0; i < category.length; i++) {

            ConfigEntry entry = table.get(category[i]);

            if (entry != null && !entry.getType().isSynthetic()) {
                String text = entry.toConf();
                if (text != null && text.length() > 0) {
                    buf.append(entry.toConf());
                }
            }
        }
    }

    /**
     * Build an ordered map so that it displays in a consistent order.
     */
    private void toOSIS(OSISUtil.OSISFactory factory, Element ele, String aTitle, Map<String,ConfigEntry> map) {
        Element title = null;
        for (Map.Entry<String,ConfigEntry> mapEntry : map.entrySet()) {
            ConfigEntry entry = mapEntry.getValue();
            Element configElement = null;

            if (entry != null) {
                configElement = entry.toOSIS();
            }

            if (title == null && configElement != null) {
                // I18N(DMS): use aTitle to lookup translation.
                title = factory.createHeader();
                title.addContent(aTitle);
                ele.addContent(title);
            }

            if (configElement != null) {
                ele.addContent(configElement);
            }
        }
    }

    private void toConf(StringBuilder buf, Map<String,ConfigEntry> map) {
        for (Map.Entry<String,ConfigEntry> mapEntry : map.entrySet()) {
            ConfigEntry entry = mapEntry.getValue();
            String text = entry.toConf();
            if (text != null && text.length() > 0) {
                buf.append(text);
            }
        }
    }

    private String report(String issue, String confEntryName, String line) {
        StringBuilder buf = new StringBuilder(100);
        buf.append(issue);
        buf.append(' ');
        buf.append(confEntryName);
        buf.append(" in ");
        buf.append(internal);
        buf.append(": ");
        buf.append(line);

        return buf.toString();
    }

    /**
     * Sword only recognizes two encodings for its modules: UTF-8 and LATIN1
     * Sword uses MS Windows cp1252 for Latin 1 not the standard. Arrgh!
     */
    private static final String ENCODING_UTF8 = "UTF-8";
    private static final String ENCODING_LATIN1 = "WINDOWS-1252";

    /**
     * These are the elements that JSword requires. They are a superset of those
     * that Sword requires.
     */
    /*
     * For documentation purposes at this time. private static final
     * ConfigEntryType[] REQUIRED = { ConfigEntryType.INITIALS,
     * ConfigEntryType.DESCRIPTION, ConfigEntryType.CATEGORY, // may not be
     * present in conf ConfigEntryType.DATA_PATH, ConfigEntryType.MOD_DRV, };
     */

    private static final ConfigEntryType[] BASIC_INFO = {
            ConfigEntryType.INITIALS, ConfigEntryType.DESCRIPTION, ConfigEntryType.CATEGORY, ConfigEntryType.LCSH, ConfigEntryType.SWORD_VERSION_DATE,
            ConfigEntryType.VERSION, ConfigEntryType.HISTORY, ConfigEntryType.OBSOLETES, ConfigEntryType.INSTALL_SIZE,
    };

    private static final ConfigEntryType[] LANG_INFO = {
            ConfigEntryType.LANG, ConfigEntryType.GLOSSARY_FROM, ConfigEntryType.GLOSSARY_TO,
    };

    private static final ConfigEntryType[] COPYRIGHT_INFO = {
            ConfigEntryType.ABOUT, ConfigEntryType.SHORT_PROMO, ConfigEntryType.DISTRIBUTION_LICENSE, ConfigEntryType.DISTRIBUTION_NOTES,
            ConfigEntryType.DISTRIBUTION_SOURCE, ConfigEntryType.SHORT_COPYRIGHT, ConfigEntryType.COPYRIGHT, ConfigEntryType.COPYRIGHT_DATE,
            ConfigEntryType.COPYRIGHT_HOLDER, ConfigEntryType.COPYRIGHT_CONTACT_NAME, ConfigEntryType.COPYRIGHT_CONTACT_ADDRESS,
            ConfigEntryType.COPYRIGHT_CONTACT_EMAIL, ConfigEntryType.COPYRIGHT_CONTACT_NOTES, ConfigEntryType.COPYRIGHT_NOTES, ConfigEntryType.TEXT_SOURCE,
    };

    private static final ConfigEntryType[] FEATURE_INFO = {
            ConfigEntryType.FEATURE, ConfigEntryType.GLOBAL_OPTION_FILTER, ConfigEntryType.FONT,
    };

    private static final ConfigEntryType[] SYSTEM_INFO = {
            ConfigEntryType.DATA_PATH, ConfigEntryType.MOD_DRV, ConfigEntryType.SOURCE_TYPE, ConfigEntryType.BLOCK_TYPE, ConfigEntryType.BLOCK_COUNT,
            ConfigEntryType.COMPRESS_TYPE, ConfigEntryType.ENCODING, ConfigEntryType.MINIMUM_VERSION, ConfigEntryType.OSIS_VERSION,
            ConfigEntryType.OSIS_Q_TO_TICK, ConfigEntryType.DIRECTION, ConfigEntryType.KEY_TYPE, ConfigEntryType.DISPLAY_LEVEL,
    };

    private static final ConfigEntryType[] HIDDEN = {
        ConfigEntryType.CIPHER_KEY,
    };

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(ConfigEntryTable.class);

    /**
     * The original name of this config file from mods.d. This is only used for
     * managing warnings and errors
     */
    private String internal;

    /**
     * A map of lists of known config entries.
     */
    private Map<ConfigEntryType,ConfigEntry> table;

    /**
     * A map of lists of unknown config entries.
     */
    private Map<String,ConfigEntry> extra;

    /**
     * The BookType for this ConfigEntry
     */
    private BookType bookType;

    /**
     * True if this book's config type can be used by JSword.
     */
    private boolean supported;

    /**
     * True if this book is considered questionable.
     */
    private boolean questionable;

    /**
     * A helper for the reading of the conf file.
     */
    private String readahead;

    /**
     * If the module's config is tied to a file remember it so that it can be
     * updated.
     */
    private File configFile;

    /**
     * Pattern that matches a key=value. The key can contain ascii letters,
     * numbers, underscore and period. The key must begin at the beginning of
     * the line. The = sign following the key may be surrounded by whitespace.
     * The value may contain anything, including an = sign.
     */
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^([A-Za-z0-9_.]+)\\s*=\\s*(.*)$");

}
