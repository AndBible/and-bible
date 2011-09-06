package net.bible.service.format.osistohtml.preprocessor;

/** preprocess text content in the Sword module
 * 
 * @author denha1m
 *
 */
public interface TextPreprocessor {
	/* convert module text to that required for display
	 */
	String process(String text);
}
