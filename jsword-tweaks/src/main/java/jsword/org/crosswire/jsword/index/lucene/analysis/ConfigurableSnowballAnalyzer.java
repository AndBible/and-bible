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
 * Copyright: 2007
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id:  $
 */
package org.crosswire.jsword.index.lucene.analysis;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.LowerCaseTokenizer;
import org.apache.lucene.analysis.PorterStemFilter;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.util.Version;
import org.crosswire.jsword.book.Book;

/**
 * An Analyzer whose {@link TokenStream} is built from a
 * {@link LowerCaseTokenizer} filtered with {@link SnowballFilter} (optional)
 * and {@link StopFilter} (optional) Default behavior: Stemming is done, Stop
 * words not removed A snowball stemmer is configured according to the language
 * of the Book. Currently it takes following stemmer names (available stemmers
 * in lucene snowball package net.sf.snowball.ext)
 * 
 * <pre>
 *     Danish
 *     Dutch
 *     English
 *     Finnish
 *     French
 *     German2
 *     German
 *     Italian
 *     Kp
 *     Lovins
 *     Norwegian
 *     Porter
 *     Portuguese
 *     Russian
 *     Spanish
 *     Swedish
 * </pre>
 * 
 * This list is expected to expand, as and when Snowball project support more
 * languages
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author sijo cherian [sijocherian at yahoo dot com]
 */
public class ConfigurableSnowballAnalyzer extends AbstractBookAnalyzer {
    public ConfigurableSnowballAnalyzer() {
    }

    /**
     * Filters {@link LowerCaseTokenizer} with {@link StopFilter} if enabled and
     * {@link SnowballFilter}.
     */
    @Override
    public final TokenStream tokenStream(String fieldName, Reader reader) {
        TokenStream result = new LowerCaseTokenizer(reader);
        if (doStopWords && stopSet != null) {
            result = new StopFilter(false, result, stopSet);
        }

        // Configure Snowball filter based on language/stemmerName
        if (doStemming) {
            result = new SnowballFilter(result, stemmerName);
        }

        return result;
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.analysis.Analyzer#reusableTokenStream(java.lang.String, java.io.Reader)
     */
    @Override
    public TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
        SavedStreams streams = (SavedStreams) getPreviousTokenStream();
        if (streams == null) {
            streams = new SavedStreams(new LowerCaseTokenizer(reader));
            if (doStopWords && stopSet != null) {
                streams.setResult(new StopFilter(StopFilter.getEnablePositionIncrementsVersionDefault(matchVersion), streams.getResult(), stopSet));
            }

            if (doStemming) {
                streams.setResult(new PorterStemFilter(streams.getResult()));
            }

            setPreviousTokenStream(streams);
        } else {
            streams.getSource().reset(reader);
        }
        return streams.getResult();
    }

    @Override
    public void setBook(Book newBook) {
        book = newBook;
        stemmerName = null;
        if (book != null) {
            // stemmer name are same as language name, in most cases
            pickStemmer(book.getLanguage().getCode());
        }
    }

    /**
     * Given the name of a stemmer, use that one.
     * 
     * @param languageCode
     */
    public void pickStemmer(String languageCode) {
        if (languageCode != null) {
            // Check for allowed stemmers
            if (languageCodeToStemmerLanguageNameMap.containsKey(languageCode)) {
                stemmerName = languageCodeToStemmerLanguageNameMap.get(languageCode);
            } else {
                throw new IllegalArgumentException("SnowballAnalyzer configured for unavailable stemmer " + stemmerName);
            } 

            // Initialize the default stop words
            if (defaultStopWordMap.containsKey(languageCode)) {
                stopSet = defaultStopWordMap.get(languageCode);
            }
        }
    }

    /**
     * The name of the stemmer to use.
     */
    private String stemmerName;

    private static Map<String, String> languageCodeToStemmerLanguageNameMap = new HashMap<String, String>();
    static {
    	languageCodeToStemmerLanguageNameMap.put("da", "Danish");
    	languageCodeToStemmerLanguageNameMap.put("nl", "Dutch");
    	languageCodeToStemmerLanguageNameMap.put("en", "English");
    	languageCodeToStemmerLanguageNameMap.put("fi", "Finnish");
    	languageCodeToStemmerLanguageNameMap.put("fr", "French");
    	languageCodeToStemmerLanguageNameMap.put("de", "German");
    	languageCodeToStemmerLanguageNameMap.put("it", "Italian");
    	languageCodeToStemmerLanguageNameMap.put("no", "Norwegian");
    	languageCodeToStemmerLanguageNameMap.put("pt", "Portuguese");
    	languageCodeToStemmerLanguageNameMap.put("ru", "Russian");
    	languageCodeToStemmerLanguageNameMap.put("es", "Spanish");
    	languageCodeToStemmerLanguageNameMap.put("sv", "Swedish");
    }

    // Maps StemmerName > String array of standard stop words
    private static HashMap<String, Set<?>> defaultStopWordMap = new HashMap<String, Set<?>>();
    static {
        defaultStopWordMap.put("fr", FrenchAnalyzer.getDefaultStopSet());
        defaultStopWordMap.put("de", GermanAnalyzer.getDefaultStopSet());
        defaultStopWordMap.put("nl", DutchAnalyzer.getDefaultStopSet());
        defaultStopWordMap.put("en", StopAnalyzer.ENGLISH_STOP_WORDS_SET);
    }

    private final Version matchVersion = Version.LUCENE_29;
}
