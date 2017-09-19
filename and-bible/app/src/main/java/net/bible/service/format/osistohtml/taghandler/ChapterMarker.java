package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.xml.sax.Attributes;

/**
 * Surround whole verse with
 *    <span class='verse' id='N'><span class='verseNo'>N</span>verse text here</span>
 * Write the verse number at the beginning of a verse
 * Also handle verse per line
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class ChapterMarker implements OsisTagHandler {

	private OsisToHtmlParameters parameters;

	private HtmlTextWriter writer;

	public ChapterMarker(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}

	@Override
	public String getTagName() {
		return "NotLinkedToOsisChapterElement";
	}

	@Override
	public void start(Attributes attrs) {
		Integer chapter = parameters.getChapter();
		if (chapter !=null && chapter>1 && parameters.isShowVerseNumbers()) {
			writer.write("<div class='chapterNo'>&#8212; "+chapter+" &#8212;</div>");
		}
	}

	@Override
	public void end() {
	}
}
