package net.bible.service.format.osistohtml.preprocessor;

/** preprocess text content in the Sword module
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface TextPreprocessor {
    /* convert module text to that required for display
     */
    String process(String text);
}
