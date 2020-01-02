/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.service.font;

import net.bible.android.SharedConstants;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.download.GenericFileDownloader;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.install.InstallException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class FontControl {

    private Properties fontProperties = new Properties(); 
    
    private static final String FONT_DOWNLOAD_URL = "http://www.crosswire.org/and-bible/fonts/v1/";
    private static String FONT_PROPERTIES_FILENAME = "fonts.properties";
    private static String FONT_SIZE_ADJUSTMENT = ".fontSizeAdjustment";
    private static String CSS_CLASS = ".cssClass";
    
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
		String font = null;
		try {
			if (book!=null) {
				// sometimes an error occurs on following line - maybe due to missing language info in book metadata
				String langCode = book.getBookMetaData().getLanguage().getCode();
				// is there a font for the book
				final String abbreviation = book.getAbbreviation();
				font = fontProperties.getProperty(abbreviation);
				// is there a font for the language code
				if (StringUtils.isEmpty(font)) {
					font = fontProperties.getProperty(langCode);
				}

				log.debug("Book:" + abbreviation + " Language code:" + langCode + " Font:" + font);
			}
		} catch (Exception e) {
			// sometimes get here if a book has no initials - so do not attempt to print a books initials in the error 
			log.warn("Problem getting font for book", e);
		}
		return font;
	}

	/** SBLGNT is a bit small so font size  needs to be adjusted up, but the same method can be used for other fonts
	 * adding
	 *  fontname.fontSizeAdjustment=2
	 * will increase the size when fontname is used
	 */
	public int getFontSizeAdjustment(String font, Book book) {
		int sizeAdjustment = 0;
		try {
			if (!StringUtils.isEmpty(font)) {
				// only modify font size if font is for whole page otherwise some characters are different sizes which looks odd
				if (StringUtils.isEmpty(getCssClassForCustomFont(book))) {
					String sizeAdjustmentString = fontProperties.getProperty(font+FONT_SIZE_ADJUSTMENT, "0");
					sizeAdjustment = Integer.parseInt(sizeAdjustmentString);
				}
			}
		} catch (Exception e) {
			log.error("Error getting font size adjustment", e);
		}
		return sizeAdjustment;
	}

	public String getCssClassForCustomFont(Book book) {
		return fontProperties.getProperty(book.getAbbreviation()+CSS_CLASS, "");
	}
	

	public boolean exists(String font) {
		return 	new File(SharedConstants.FONT_DIR, font).exists() ||
				new File(SharedConstants.MANUAL_FONT_DIR, font).exists();
	}
	
	public String getHtmlFontStyle(String font, String cssClass) {
		String fontStyle = "";
		if (!StringUtils.isEmpty(font)) {
			if (StringUtils.isEmpty(cssClass)) {
				cssClass = "body";
			}
			
			File fontFile = getFontFile(font);
			if (fontFile!=null && fontFile.exists()) {
				fontStyle = "<style>@font-face {font-family: 'CustomFont';src: url('"+getFontFile(font).toURI()+"'); font-weight:normal; font-style:normal; font-variant:normal;} "+
							cssClass+" {font-family: 'CustomFont', 'Droid Sans';}</style>";
				// if range specified the default font also changes e.g. unicode-range:U+0370-03FF,U+1F00-1FFE;
			} else {
				log.error("Font not found:"+font);
			}
		}
		return fontStyle;
	}

	public void downloadFont(String font) throws InstallException {
		log.debug("Download font "+font);
		URI source;
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
			URI source;
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
   		fontProperties.putAll(CommonUtils.INSTANCE.loadProperties(fontPropFile));

   		// load font properties from manual install dir
   		fontPropFile = new File(SharedConstants.MANUAL_FONT_DIR, FONT_PROPERTIES_FILENAME);
   		fontProperties.putAll(CommonUtils.INSTANCE.loadProperties(fontPropFile));
	}
	
	/** find font in manual or default font dir
	 */
	public File getFontFile(String font) {
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
