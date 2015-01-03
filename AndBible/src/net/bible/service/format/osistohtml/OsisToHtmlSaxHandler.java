package net.bible.service.format.osistohtml;

import java.util.List;

import net.bible.service.common.Constants.HTML;
import net.bible.service.common.Logger;
import net.bible.service.device.ScreenSettings;
import net.bible.service.font.FontControl;
import net.bible.service.format.Note;
import net.bible.service.format.OsisSaxHandler;
import net.bible.service.format.osistohtml.preprocessor.HebrewCharacterPreprocessor;
import net.bible.service.format.osistohtml.preprocessor.TextPreprocessor;
import net.bible.service.format.osistohtml.strongs.StrongsHandler;
import net.bible.service.format.osistohtml.strongs.StrongsLinkCreator;
import net.bible.service.format.osistohtml.tei.OrthHandler;
import net.bible.service.format.osistohtml.tei.PronHandler;
import net.bible.service.format.osistohtml.tei.RefHandler;
import net.bible.service.format.osistohtml.tei.TEIUtil;

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
	private MyNoteMarker myNoteMarker;
	private BookmarkMarker bookmarkMarker;
	private NoteHandler noteHandler;
	private ReferenceHandler referenceHandler;
	private RefHandler refHandler;
	private TitleHandler titleHandler;
	private QHandler qHandler;
	private MilestoneHandler milestoneHandler;
	private LGHandler lgHandler;
	private LHandler lHandler;
	private HiHandler hiHandler;
	private OrthHandler orthHandler;
	private PronHandler pronHandler;
	private StrongsHandler strongsHandler;
	private FigureHandler figureHandler;
	private DivHandler divHandler;
	
	private TableHandler tableHandler;
	private TableRowHandler tableRowHandler;
	private TableCellHandler tableCellHandler;
	
	// processor for the tag content
	private TextPreprocessor textPreprocessor;

	// internal logic
	private VerseInfo verseInfo = new VerseInfo();
	public static class VerseInfo {
		int currentVerseNo;
		int positionToInsertBeforeVerse;
		boolean isTextSinceVerse = false;
	}
	
	private PassageInfo passageInfo = new PassageInfo();
	public static class PassageInfo {
		boolean isAnyTextWritten = false;
	}
	
	private static final String HEBREW_LANGUAGE_CODE = "he";

	private static final Logger log = new Logger("OsisToHtmlSaxHandler");

	public OsisToHtmlSaxHandler(OsisToHtmlParameters parameters) {
		super();
		this.parameters = parameters;
		verseHandler = new VerseHandler(parameters, verseInfo, getWriter());
		myNoteMarker = new MyNoteMarker(parameters, verseInfo, getWriter());
		bookmarkMarker = new BookmarkMarker(parameters, verseInfo, getWriter());
		noteHandler = new NoteHandler(parameters, verseInfo, getWriter());
		referenceHandler = new ReferenceHandler(parameters, noteHandler, getWriter());
		refHandler = new RefHandler(parameters, noteHandler, getWriter());
		titleHandler = new TitleHandler(parameters, verseInfo, getWriter());
		qHandler = new QHandler(parameters, getWriter());
		milestoneHandler = new MilestoneHandler(parameters, passageInfo, verseInfo, getWriter());
		hiHandler = new HiHandler(parameters, getWriter());
		orthHandler = new OrthHandler(parameters, getWriter());
		pronHandler = new PronHandler(parameters, getWriter());
		lgHandler = new LGHandler(parameters, getWriter());
		lHandler = new LHandler(parameters, getWriter());
		strongsHandler = new StrongsHandler(parameters, getWriter());
		figureHandler = new FigureHandler(parameters, getWriter());
		divHandler = new DivHandler(parameters, passageInfo, getWriter());
		tableHandler = new TableHandler(getWriter());
		tableRowHandler = new TableRowHandler(getWriter());
		tableCellHandler = new TableCellHandler(getWriter());

		//TODO at the moment we can only have a single TextPreprocesor, need to chain them and maybe make the writer a TextPreprocessor and put it at the end of the chain
		if (HEBREW_LANGUAGE_CODE.equals(parameters.getLanguageCode())) {
			textPreprocessor = new HebrewCharacterPreprocessor();
		} else if (parameters.isConvertStrongsRefsToLinks()) {
			textPreprocessor = new StrongsLinkCreator();
		}
	}

	@Override
	public void startDocument()  {
		String jsTag = "\n<script type='text/javascript' src='file:///android_asset/web/script.js'></script>\n";
		String styleSheetTag = "<link href='file:///android_asset/web/style.css' rel='stylesheet' type='text/css'/>";
		String extraStyleSheetTag = "";
		if (parameters.getExtraStylesheet() != null) {
			extraStyleSheetTag = "<link href='file:///android_asset/web/"
					+ parameters.getExtraStylesheet()
					+ "' rel='stylesheet' type='text/css'/>";
		}
		String customFontStyle = FontControl.getInstance().getHtmlFontStyle(parameters.getFont(), parameters.getCssClassForCustomFont());
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
			bookmarkMarker.start();
			myNoteMarker.start();
			// record that we are into a new verse
			verseInfo.isTextSinceVerse = false;
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE)) {
			titleHandler.start(attrs);		
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			noteHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			referenceHandler.start(attrs);
		} else if (name.equals(TEIUtil.TEI_ELEMENT_REF)) {
			refHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_LB)) {
			if (passageInfo.isAnyTextWritten) {
				write(HTML.BR);
			}
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_LG)) {
			lgHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
			lHandler.startL(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_DIV)) {
			divHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_P)) {
			write("<p>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q)) {
			qHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_HI)) {
			hiHandler.start(attrs);
		} else if (name.equals(TEIUtil.TEI_ELEMENT_ORTH)) {
			orthHandler.start(attrs);
		} else if (name.equals(TEIUtil.TEI_ELEMENT_PRON)) {
			pronHandler.start(attrs);
		} else if (name.equals(OSISUtil2.OSIS_ELEMENT_MILESTONE)) {
			milestoneHandler.start(attrs);
		} else if (name.equals("transChange")) {
			write("<span class='transChange'>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_W)) {
			strongsHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TABLE)) {
			tableHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_ROW)) {
			tableRowHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_CELL)) {
			tableCellHandler.start(attrs);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_FIGURE)) {
			figureHandler.start(attrs);
		} else {
			log.info("Verse "+verseInfo.currentVerseNo+" unsupported OSIS tag:"+name);
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
			myNoteMarker.end();
			bookmarkMarker.end();
			verseHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			noteHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			referenceHandler.end();
		} else if (name.equals(TEIUtil.TEI_ELEMENT_REF)) {
			refHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_LG)) {
			lgHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
			lHandler.endL();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_DIV)) {
			divHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_P)) {
			write("</p>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q)) {
			// end quotation, but <q /> tag is a marker and contains no content
			// so <q /> will appear at beginning and end of speech
			qHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_HI)) {
			hiHandler.end();
		} else if (name.equals(TEIUtil.TEI_ELEMENT_ORTH)) {
			orthHandler.end();
		} else if (name.equals(TEIUtil.TEI_ELEMENT_PRON)) {
			pronHandler.end();
		} else if (name.equals("transChange")) {
			write("</span>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_W)) {
			strongsHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TABLE)) {
			tableHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_ROW)) {
			tableRowHandler.end();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_CELL)) {
			tableCellHandler.end();
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
		
		// record that we are now beyond the verse, but do it quickly so as not to slow down parsing
		verseInfo.isTextSinceVerse = verseInfo.isTextSinceVerse ||
										len>2 ||
										StringUtils.isNotBlank(s);
		passageInfo.isAnyTextWritten = passageInfo.isAnyTextWritten || verseInfo.isTextSinceVerse;
		
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
		// the pure padding is the height of the WebView - one line height to keep one line on the screen
		// but some books already contain padding (br) at end so I fudge by multiplying line height by 2 to try to avoid all text scrolling off screen
		// this is not very accurate.  Some books have a <br />s at the end making the padding too large
		// also the user can toggle full screen after the last view height calculation
		// 1.5 is a fudge factor to try to keep a little of the text on the screen for books that end in a <br /> 
		int paddingHeightDips = (int)(ScreenSettings.getContentViewHeightDips()-(2*ScreenSettings.getLineHeightDips()));
		return "<img height='"+paddingHeightDips+"' width='1' border='0' vspace='0' style='display:block'/>"; 
	}

	public List<Note> getNotesList() {
		return noteHandler.getNotesList();
	}
}
