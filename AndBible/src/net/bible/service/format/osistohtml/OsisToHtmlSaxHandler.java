package net.bible.service.format.osistohtml;

import java.util.List;

import net.bible.service.common.Constants.HTML;
import net.bible.service.common.Logger;
import net.bible.service.font.FontControl;
import net.bible.service.format.Note;
import net.bible.service.format.OsisSaxHandler;
import net.bible.service.format.osistohtml.preprocessor.HebrewCharacterPreprocessor;
import net.bible.service.format.osistohtml.preprocessor.TextPreprocessor;
import net.bible.service.format.osistohtml.strongs.StrongsHandler;
import net.bible.service.format.osistohtml.strongs.StrongsLinkCreator;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/**
 * Convert OSIS tags into html tags
 * 
 * Example OSIS tags from KJV Ps 119 v1 showing title, w, note <title
 * canonical="true" subType="x-preverse" type="section"> <foreign
 * n="?">ALEPH.</foreign> </title> <w lemma="strong:H0835">Blessed</w>
 * <transChange type="added">are</transChange> <w lemma="strong:H08549">the
 * undefiled</w> ... <w lemma="strong:H01980" morph="strongMorph:TH8802">who
 * walk</w> ... <w lemma="strong:H03068">of the
 * <seg><divineName>Lord</divineName></seg></w>. <note type="study">undefiled:
 * or, perfect, or, sincere</note>
 * 
 * Example of notes cross references from ESV In the <note n="a"
 * osisID="Gen.1.1!crossReference.a" osisRef="Gen.1.1"
 * type="crossReference"><reference osisRef="Job.38.4-Job.38.7">Job
 * 38:4-7</reference>; <reference osisRef="Ps.33.6">Ps. 33:6</reference>;
 * <reference osisRef="Ps.136.5">136:5</reference>; <reference
 * osisRef="Isa.42.5">Isa. 42:5</reference>; <reference
 * osisRef="Isa.45.18">45:18</reference>; <reference
 * osisRef="John.1.1-John.1.3">John 1:1-3</reference>; <reference
 * osisRef="Acts.14.15">Acts 14:15</reference>; <reference
 * osisRef="Acts.17.24">17:24</reference>; <reference
 * osisRef="Col.1.16-Col.1.17">Col. 1:16, 17</reference>; <reference
 * osisRef="Heb.1.10">Heb. 1:10</reference>; <reference
 * osisRef="Heb.11.3">11:3</reference>; <reference osisRef="Rev.4.11">Rev.
 * 4:11</reference></note>beginning
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class OsisToHtmlSaxHandler extends OsisSaxHandler {

	// properties
	private OsisToHtmlParameters parameters;

	// tag handlers for the different OSIS tags
	private VerseHandler verseHandler;
	private NoteAndReferenceHandler noteAndReferenceHandler;
	private TitleHandler titleHandler;
	private QHandler qHandler;
	private LHandler lHandler;
	private StrongsHandler strongsHandler;
	
	// processor for the tag content
	private TextPreprocessor textPreprocessor;

	// internal logic
	private VerseInfo verseInfo = new VerseInfo();
	class VerseInfo {
		int currentVerseNo;
		int currentVersePosition;
	}

	private static final String HEBREW_LANGUAGE_CODE = "he";

	@SuppressWarnings("unused")
	private static final Logger log = new Logger("OsisToHtmlSaxHandler");

	public OsisToHtmlSaxHandler(OsisToHtmlParameters parameters) {
		super();
		this.parameters = parameters;
		verseHandler = new VerseHandler(parameters, verseInfo, getWriter());
		noteAndReferenceHandler = new NoteAndReferenceHandler(parameters, getWriter());
		titleHandler = new TitleHandler(parameters, verseInfo, getWriter());
		qHandler = new QHandler(parameters, getWriter());
		lHandler = new LHandler(parameters, getWriter());
		strongsHandler = new StrongsHandler(parameters, getWriter());
		
		//TODO at the moment we can only have a single TextPreprocesor, need to chain them and maybe make the writer a TextPreprocessor and put it at the end of the chain
		if (HEBREW_LANGUAGE_CODE.equals(parameters.getLanguageCode())) {
			textPreprocessor = new HebrewCharacterPreprocessor();
		} else if (parameters.isConvertStrongsRefsToLinks()) {
			textPreprocessor = new StrongsLinkCreator();
		}

	}

	@Override
	public void startDocument()  {
		String jsTag = "\n<script type='text/javascript' src='file:///android_asset/script.js'></script>\n";
		String styleSheetTag = "<link href='file:///android_asset/style.css' rel='stylesheet' type='text/css'/>";
		String extraStyleSheetTag = "";
		if (parameters.getExtraStylesheet() != null) {
			extraStyleSheetTag = "<link href='file:///android_asset/"
					+ parameters.getExtraStylesheet()
					+ "' rel='stylesheet' type='text/css'/>";
		}
		String customFontStyle = FontControl.getInstance().getHtmlFontStyle(parameters.getFont());
		write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"> "
				+ "<html xmlns='http://www.w3.org/1999/xhtml' dir='" + getDirection() + "'><head>"
				+ styleSheetTag + extraStyleSheetTag+"\n"
				+ customFontStyle
				+ jsTag
				+ "<meta charset='utf-8'/>"
				+ "</head>"
				+ "<body onscroll='jsonscroll()' onload='jsonload()' >");

		// force rtl for rtl languages - rtl support on Android is poor but
		// forcing it seems to help occasionally
		if (!parameters.isLeftToRight()) {
			write("<span dir='rtl'>");
		}
	}

	/*
	 * Called when the Parser Completes parsing the Current XML File.
	 */
	@Override
	public void endDocument() {

		// close last verse
		if (parameters.isVersePerline()) {
			//close last verse
			if (verseInfo.currentVerseNo>1) {
				write("</div>");
			}
		}
		
		// add optional footer e.g. Strongs show all occurrences link
		if (StringUtils.isNotEmpty(parameters.getExtraFooter())) {
			write(parameters.getExtraFooter());
		}

		if (!parameters.isLeftToRight()) {
			write("</span>");
		}
		// add padding at bottom to allow last verse to scroll to top of page
		// and become current verse
		write(getPaddingAtBottom() + "</body></html>");
	}

	/*
	 * Called when the starting of the Element is reached. For Example if we
	 * have Tag called <Title> ... </Title>, then this method is called when
	 * <Title> tag is Encountered while parsing the Current XML File. The
	 * AttributeList Parameter has the list of all Attributes declared for the
	 * Current Element in the XML File.
	 */
	@Override
    public void startElement(String namespaceURI,
            String sName, // simple name
            String qName, // qualified name
            Attributes attrs)
    {
		String name = getName(sName, qName); // element name

		debug(name, attrs, true);

		if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			verseHandler.startAndUpdateVerse(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE)) {
			titleHandler.start(attrs);		
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			noteAndReferenceHandler.startNote(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			noteAndReferenceHandler.startReference(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_LB)) {
			write(HTML.BR);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
			lHandler.startL(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_P)) {
			write("<p />");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q)) {
			qHandler.start(attrs);
		} else if (name.equals("milestone")) {
			String type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE);
			if (StringUtils.isNotEmpty(type)) {
				if (type.equals("line") || type.equals("x-p")) {
					//e.g. NETtext Mt 4:14
					write(HTML.BR);
				}
			}
		} else if (name.equals("transChange")) {
			write("<span class='transChange'>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_W)) {
			strongsHandler.start(attrs);
		} else {
//			log.info("Verse "+currentVerseNo+" unsupported OSIS tag:"+name);
		}
	}

	/*
	 * Called when the Ending of the current Element is reached. For example in
	 * the above explanation, this method is called when </Title> tag is reached
	 */
	@Override
	public void endElement(String namespaceURI, String sName, // simple name
			String qName // qualified name
	) {
		String name = getName(sName, qName);

		debug(name, null, false);

		if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE)) {
			titleHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			// verse opening and closing tags wrap the verse number at start of the verse
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			noteAndReferenceHandler.endNote(verseInfo.currentVerseNo);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			noteAndReferenceHandler.endReference(verseInfo.currentVerseNo);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
			lHandler.endL();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q)) {
			// end quotation, but <q /> tag is a marker and contains no content
			// so <q /> will appear at beginning and end of speech
			qHandler.end();
		} else if (name.equals("transChange")) {
			write("</span>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_W)) {
			strongsHandler.end();
		}
	}

	/*
	 * While Parsing the XML file, if extra characters like space or enter
	 * Character are encountered then this method is called. If you don't want
	 * to do anything special with these characters, then you can normally leave
	 * this method blank.
	 */
	@Override
	public void characters(char buf[], int offset, int len) {
		String s = new String(buf, offset, len);
		
		if (textPreprocessor!=null) {
			s = textPreprocessor.process(s);
		}
		
		write(s);
	}

	/*
	 * In the XML File if the parser encounters a Processing Instruction which
	 * is declared like this <?ProgramName:BooksLib
	 * QUERY="author, isbn, price"?> Then this method is called where Target
	 * parameter will have "ProgramName:BooksLib" and data parameter will have
	 * QUERY="author, isbn, price". You can invoke a External Program from this
	 * Method if required.
	 */
	public void processingInstruction(String target, String data) {
		// noop
	}

	public String getDirection() {
		return parameters.isLeftToRight() ? "ltr" : "rtl";
	}

	private String getPaddingAtBottom() {
		return StringUtils.repeat(HTML.BR, parameters
				.getNumPaddingBrsAtBottom());
	}

	public List<Note> getNotesList() {
		return noteAndReferenceHandler.getNotesList();
	}
}
