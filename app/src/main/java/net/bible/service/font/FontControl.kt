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
package net.bible.service.font

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.service.common.CommonUtils.loadProperties
import net.bible.service.common.Logger
import net.bible.service.download.GenericFileDownloader
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.install.InstallException
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class FontControl private constructor() {
    private val fontProperties = Properties()

    fun getFontForBook(book: Book?): String? {
        var font: String? = null
        try {
            if (book != null) {
                // sometimes an error occurs on following line - maybe due to missing language info in book metadata
                val langCode = book.bookMetaData.language.code
                // is there a font for the book
                val abbreviation = book.abbreviation
                font = fontProperties.getProperty(abbreviation)
                // is there a font for the language code
                if (StringUtils.isEmpty(font)) {
                    font = fontProperties.getProperty(langCode)
                }
                log.debug("Book:$abbreviation Language code:$langCode Font:$font")
            }
        } catch (e: Exception) {
            // sometimes get here if a book has no initials - so do not attempt to print a books initials in the error
            log.warn("Problem getting font for book", e)
        }
        return font
    }

    /** SBLGNT is a bit small so font size  needs to be adjusted up, but the same method can be used for other fonts
     * adding
     * fontname.fontSizeAdjustment=2
     * will increase the size when fontname is used
     */
    fun getFontSizeAdjustment(font: String?, book: Book?): Int {
        var sizeAdjustment = 0
        try {
            if (!StringUtils.isEmpty(font) && StringUtils.isEmpty(getCssClassForCustomFont(book))) {
                val sizeAdjustmentString = fontProperties.getProperty(
                    font + FONT_SIZE_ADJUSTMENT, "0"
                )
                sizeAdjustment = sizeAdjustmentString.toInt()
            }
        } catch (e: Exception) {
            log.error("Error getting font size adjustment", e)
        }
        return sizeAdjustment
    }

    fun getCssClassForCustomFont(book: Book?): String {
        return fontProperties.getProperty((book?.abbreviation?:"") + CSS_CLASS, "")
    }

    fun exists(font: String?): Boolean {
        return File(SharedConstants.FONT_DIR, font).exists() ||
            File(SharedConstants.MANUAL_FONT_DIR, font).exists()
    }

    fun getHtmlFontStyle(font: String?, cssClass: String?): String {
        var cssClass = cssClass
        var fontStyle = ""
        if (!StringUtils.isEmpty(font)) {
            if (StringUtils.isEmpty(cssClass)) {
                cssClass = "body"
            }
            val fontFile = getFontFile(font)
            if (fontFile != null && fontFile.exists()) {
                fontStyle = "<style>@font-face {font-family: 'CustomFont';src: url('" + getFontFile(font)!!.toURI() + "'); font-weight:normal; font-style:normal; font-variant:normal;} " +
                    cssClass + " {font-family: 'CustomFont', 'Droid Sans';}</style>"
                // if range specified the default font also changes e.g. unicode-range:U+0370-03FF,U+1F00-1FFE;
            } else {
                log.error("Font not found:$font")
            }
        }
        return fontStyle
    }

    @Throws(InstallException::class)
    fun downloadFont(font: String) {
        log.debug("Download font $font")
        val source: URI
        source = try {
            URI(FONT_DOWNLOAD_URL + font)
        } catch (use: URISyntaxException) {
            log.error("Invalid URI", use)
            throw InstallException("Error downloading font")
        }
        val target = File(SharedConstants.FONT_DIR, font)
        val downloader = GenericFileDownloader()
        downloader.downloadFileInBackground(source, target, "font")
    }

    /** if font.properties refresh requested or does not exist then download font.properties
     */
    @Throws(InstallException::class)
    fun checkFontPropertiesFile(refresh: Boolean) = GlobalScope.launch(Dispatchers.IO) {
        if (refresh || !File(SharedConstants.FONT_DIR, FONT_PROPERTIES_FILENAME).exists()) {
            log.debug("Downloading $FONT_PROPERTIES_FILENAME")
            val source: URI
            source = try {
                URI(FONT_DOWNLOAD_URL + FONT_PROPERTIES_FILENAME)
            } catch (use: URISyntaxException) {
                log.error("Invalid URI", use)
                throw InstallException("Error downloading font")
            }
            val target = File(SharedConstants.FONT_DIR, FONT_PROPERTIES_FILENAME)
            val downloader = GenericFileDownloader()
            downloader.downloadFile(source, target, "font definitions")

            // now need to reload properties after new file fetched
            loadFontProperties()
        }
    }

    private fun loadFontProperties() {
        fontProperties.clear()

        // load font properties from default install dir
        var fontPropFile = File(SharedConstants.FONT_DIR, FONT_PROPERTIES_FILENAME)
        fontProperties.putAll(loadProperties(fontPropFile))

        // load font properties from manual install dir
        fontPropFile = File(SharedConstants.MANUAL_FONT_DIR, FONT_PROPERTIES_FILENAME)
        fontProperties.putAll(loadProperties(fontPropFile))
    }

    /** find font in manual or default font dir
     */
    fun getFontFile(font: String?): File? {
        var retVal: File? = null
        val autoFont = File(SharedConstants.FONT_DIR, font)
        if (autoFont.exists()) {
            retVal = autoFont
        } else {
            val manualFont = File(SharedConstants.MANUAL_FONT_DIR, font)
            if (manualFont.exists()) {
                retVal = manualFont
            } else {
                log.error("Font not found:$font")
            }
        }
        return retVal
    }

    companion object {
        private const val FONT_DOWNLOAD_URL = "https://andbible.github.io/data/fonts/v1/"
        private const val FONT_PROPERTIES_FILENAME = "fonts.properties"
        private const val FONT_SIZE_ADJUSTMENT = ".fontSizeAdjustment"
        private const val CSS_CLASS = ".cssClass"
        val instance = FontControl()
        private val log = Logger(FontControl::class.java.name)
    }

    init {
        loadFontProperties()
    }
}
