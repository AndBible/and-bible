package net.bible.service.font;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import net.bible.android.SharedConstants;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.download.GenericFileDownloader;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.install.InstallException;

public class FontControl {

    private Properties fontProperties = new Properties(); 
    
    private static final String FONT_DOWNLOAD_URL = "http://www.crosswire.org/and-bible/fonts/v1/";
    public static String FONT_PROPERTIES_FILENAME = "fonts.properties";
    
    private static FontControl SINGLETON = new FontControl();
    
	private static final Logger log = new Logger(FontControl.class.getName());
    
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

	/** SBLGNT is a bit small so font size  needs to be adjusted up, but this is a bit of a hack at the moment
	 */
	public int getFontSizeAdjustment(String font) {
		if ("SBL_grk.ttf".equals(font)) {
			return 2;
		} else {
			return 0;
		}
	}

	public boolean exists(String font) {
		return 	new File(SharedConstants.FONT_DIR, font).exists() ||
				new File(SharedConstants.MANUAL_FONT_DIR, font).exists();
	}
	
	public String getHtmlFontStyle(String font) {
		String fontStyle = "";
		if (!StringUtils.isEmpty(font)) {
			File fontFile = getFontFile(font);
			if (fontFile!=null && fontFile.exists()) {
				fontStyle = "<style>@font-face {font-family: 'CustomFont';src: url('"+getFontFile(font).toURI()+"'); font-weight:normal; font-style:normal; font-variant:normal;}"+
							"body {font-family: 'CustomFont', sans-serif;}</style>";
			} else {
				log.error("Font not found:"+font);
			}
		}
		return fontStyle;
	}

	public void downloadFont(String font) throws InstallException {
		log.debug("Download font "+font);
		URI source = null;
		try {
			source = new URI(FONT_DOWNLOAD_URL+font);
		} catch (URISyntaxException use) {
    		log.error("Invalid URI", use);
    		throw new InstallException("Error downloading font");
		}
		File target = new File(SharedConstants.FONT_DIR, font);
		
		GenericFileDownloader downloader = new GenericFileDownloader();
		downloader.downloadFileInBackground(source, target, "font");
	}

	/** if font.properties refresh requested or does not exist then download font.properties
	 */
	public void checkFontPropertiesFile(boolean refresh) throws InstallException {
		if (refresh || !(new File(SharedConstants.FONT_DIR, FONT_PROPERTIES_FILENAME).exists())) {
			log.debug("Downloading "+FONT_PROPERTIES_FILENAME);
			URI source = null;
			try {
				source = new URI(FONT_DOWNLOAD_URL+FONT_PROPERTIES_FILENAME);
			} catch (URISyntaxException use) {
	    		log.error("Invalid URI", use);
	    		throw new InstallException("Error downloading font");
			}
			File target = new File(SharedConstants.FONT_DIR, FONT_PROPERTIES_FILENAME);
			
			GenericFileDownloader downloader = new GenericFileDownloader();
			downloader.downloadFile(source, target, "font definitions");
			
			// now need to reload properties after new file fetched
			loadFontProperties();
		}
	}

	private void loadFontProperties() {
		fontProperties.clear();
		
   		// load font properties from default install dir
		File fontPropFile = new File(SharedConstants.FONT_DIR, FONT_PROPERTIES_FILENAME);
   		fontProperties.putAll(CommonUtils.loadProperties(fontPropFile));

   		// load font properties from manual install dir
   		fontPropFile = new File(SharedConstants.MANUAL_FONT_DIR, FONT_PROPERTIES_FILENAME);
   		fontProperties.putAll(CommonUtils.loadProperties(fontPropFile));
	}
	
	/** find font in manual or default font dir
	 */
	private File getFontFile(String font) {
		File retVal = null;
		File autoFont = new File(SharedConstants.FONT_DIR, font);

		if (autoFont.exists()) {
			retVal = autoFont;
		} else {
			File manualFont = new File(SharedConstants.MANUAL_FONT_DIR, font);
			if (manualFont.exists()) {
				retVal = manualFont;
			} else {
				log.error("Font not found:"+font);
			}
		}
		return retVal;
	}
}
