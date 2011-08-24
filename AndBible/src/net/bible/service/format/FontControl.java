package net.bible.service.format;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import net.bible.android.SharedConstants;
import net.bible.service.common.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.IOUtil;
import org.crosswire.jsword.book.Book;

public class FontControl {

    private static final Logger log = new Logger(FontControl.class.getName());
    
    private static String MANUAL_FONT_DIR = SharedConstants.MANUAL_INSTALL_DIR+"/fonts";
    private static String FONT_PROPERTIES_FILENAME = "fonts.properties";
    
    private Properties fontProperties = new Properties(); 
    
    private static FontControl SINGLETON = new FontControl();
    
    private FontControl() {
    	loadFontProperties();
    }
    
    public void reloadProperties() {
    	loadFontProperties();
    }
    
    public static FontControl getInstance() {
    	return SINGLETON;
    }

	public String getFontForBook(Book book) {
		String langCode = book.getBookMetaData().getLanguage().getCode();
		// is there a font for the book
		String font = fontProperties.getProperty(book.getInitials());
		// is there a font for the language code
		if (StringUtils.isEmpty(font)) {
			font = fontProperties.getProperty(langCode);
		}
		
		log.debug("Book:"+book.getInitials()+" Language code:"+langCode+" Font:"+font);
		return font;
	}
	
	public String getHtmlFontStyle(String font) {
		String fontStyle = "";
		if (!StringUtils.isEmpty(font)) {
			fontStyle = "<style>@font-face {font-family: 'CustomFont';src: url('file:///mnt/sdcard/jsword/fonts/"+font+"'); font-weight:normal; font-style:normal; font-variant:normal;}"+
						"body {font-family: 'CustomFont', sans-serif;}</style>";
		}
		return fontStyle;
	}

	private void loadFontProperties() {
    	// load font properties from manual install dir
    	File manualFontsDir = new File(MANUAL_FONT_DIR);
    	if (manualFontsDir.exists() && manualFontsDir.isDirectory()) {
    		File fontPropFile = new File(manualFontsDir, FONT_PROPERTIES_FILENAME);
    		if (fontPropFile.exists()) {
    			FileInputStream in = null;
    			try {
	            	in = new FileInputStream(fontPropFile);
	            	fontProperties.clear();
	            	fontProperties.load(in);
    			} catch (Exception e) {
    				log.error("Error loading manual font properties", e);
    			} finally {
                	IOUtil.close(in);
    			}
    		}
    	}
	}
}
