package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.xml.sax.Attributes;

/**
 * Write the chapter number at the beginning of a Bible chapter
 * The chapter id is also useful for going to the start of a chapter (verse 1)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class ChapterDivider implements OsisTagHandler {

	private OsisToHtmlParameters parameters;

	private VerseInfo verseInfo;

	private HtmlTextWriter writer;

	public ChapterDivider(OsisToHtmlParameters parameters, VerseInfo verseInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;
		this.writer = writer;
	}

	@Override
	public String getTagName() {
		return "NotLinkedToOsisChapterElement";
	}

	@Override
	public void start(Attributes attrs) {
		if (parameters.isShowChapterDivider() && parameters.getChapter()!=null) {
			Integer chapter = parameters.getChapter();
			if (chapter>1) {
				if (parameters.isShowVerseNumbers()) {
					writer.write("<div class='chapterNo'>&#8212; " + chapter + " &#8212;</div>");
				} else {
					// need some space to allow scrolling up to cause infinite-scroll to populate prior chapter
					writer.write("<div class='chapterNo'>&nbsp;</div>");
				}
			}
			// used to jump to the top of a chapter, but still allow up scroll
			writer.write("<div id='" + chapter + "'></div>");

			verseInfo.positionToInsertBeforeVerse = writer.getPosition();
		}
	}

	@Override
	public void end() {
	}
}
