package net.bible.service.format.osistohtml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bible.service.common.Constants;
import net.bible.service.common.Constants.HTML;
import net.bible.service.format.Note;
import net.bible.service.format.OsisSaxHandler;
import net.bible.service.sword.Logger;

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

	private NoteAndReferenceHandler noteAndReferenceHandler;
	private QHandler qHandler;
	private LHandler lHandler;

	// internal logic
	private int currentVerseNo;
	private int currentVersePosition;

	List<String> pendingStrongsAndMorphTags;

	// the following characters are not handled well in Android 2.2 & 2.3 and
	// need special processing which for all except Sof Pasuq means removal
	// puctuation char at the end of hebrew verses that looks like a ':'
	private static final String HEBREW_LANGUAGE_CODE = "he";
	private static final String HEBREW_SOF_PASUQ_CHAR = "\u05C3";
	// vowels are on the first row and cantillations on the second
	private static final char[] HEBREW_VOWELS_AND_CANTILLATIONS = new char[] {
			'\u05B0', '\u05B1', '\u05B2', '\u05B3', '\u05B4', '\u05B5',
			'\u05B6', '\u05B7', '\u05B8', '\u05B9', '\u05BA', '\u05BB',
			'\u05BC', '\u05BD', '\u05BE', '\u05BF', '\u05C1', '\u05C2',
			'\u0591', '\u0592', '\u0593', '\u0594', '\u0595', '\u0596',
			'\u0597', '\u0598', '\u0599', '\u059A', '\u059B', '\u059C',
			'\u059D', '\u059E', '\u05A0', '\u05A1', '\u05A2', '\u05A3',
			'\u05A4', '\u05A5', '\u05A6', '\u05A7', '\u05A8', '\u05A9',
			'\u05AA', '\u05AB', '\u05AC', '\u05AD', '\u05AE', '\u05AF' };

	private static final Logger log = new Logger("OsisToHtmlSaxHandler");

	public OsisToHtmlSaxHandler(OsisToHtmlParameters parameters) {
		super();
		this.parameters = parameters;
		noteAndReferenceHandler = new NoteAndReferenceHandler(parameters, getWriter());
		qHandler = new QHandler(parameters, getWriter());
		lHandler = new LHandler(parameters, getWriter());
	}

	@Override
	public void startDocument()  {
		log.debug("Show verses:" + parameters.isShowVerseNumbers() + " notes:"
				+ parameters.isShowNotes());
		String jsTag = "\n<script type='text/javascript' src='file:///android_asset/script.js'></script>\n";
		String styleSheetTag = "<link href='file:///android_asset/style.css' rel='stylesheet' type='text/css'/>";
		String extraStyleSheetTag = "";
		if (parameters.getExtraStylesheet() != null) {
			extraStyleSheetTag = "<link href='file:///android_asset/"
					+ parameters.getExtraStylesheet()
					+ "' rel='stylesheet' type='text/css'/>";
		}
		write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"> "
				+ "<html xmlns='http://www.w3.org/1999/xhtml' dir='" + getDirection() + "'><head>"
				+ styleSheetTag + extraStyleSheetTag
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
			//close preceding verse
			if (currentVerseNo>1) {
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
			if (attrs!=null) {
				currentVerseNo = osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
			} else {
				currentVerseNo++;
			}

			if (parameters.isVersePerline()) {
				//close preceding verse
				if (currentVerseNo>1) {
					write("</div>");
				}
				// start current verse
				write("<div>");
			}

			writeVerse(currentVerseNo);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE) && parameters.isShowHeadings()) {
				getWriter().beginInsertAt(currentVersePosition);
				write("<h1>");
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
		} else if (name.equals("transChange")) {
			write("<span class='transChange'>");
		} else if ((parameters.isShowStrongs() || parameters.isShowMorphology()) && name.equals(OSISUtil.OSIS_ELEMENT_W) && isAttr(OSISUtil.ATTRIBUTE_W_LEMMA, attrs)) {
			// Strongs & morphology references
			// example of strongs refs: <w lemma="strong:H0430">God</w> <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
			// better example, because we just use Robinson: <w lemma="strong:G652" morph="robinson:N-NSM" src="2">an apostle</w>
			String strongsLemma = attrs.getValue(OSISUtil.ATTRIBUTE_W_LEMMA);
			if (strongsLemma.startsWith(OSISUtil.LEMMA_STRONGS)) {
				String morphology = attrs.getValue(OSISUtil.ATTRIBUTE_W_MORPH);
				pendingStrongsAndMorphTags = getStrongsAndMorphTags(strongsLemma, morphology);
			}
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

		if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE) && parameters.isShowHeadings()) {
			write("</h1>");
			getWriter().finishInserting();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			// verse opening and closing tags wrap the verse number at start of the verse
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			noteAndReferenceHandler.endNote(currentVerseNo);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			noteAndReferenceHandler.endReference(currentVerseNo);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
			lHandler.endL();
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q)) {
			// end quotation, but <q /> tag is a marker and contains no content
			// so <q /> will appear at beginning and end of speech
			qHandler.end();
		} else if (name.equals("transChange")) {
			write("</span>");
		} else if ((parameters.isShowStrongs() || parameters.isShowMorphology()) && name.equals(OSISUtil.OSIS_ELEMENT_W)) {
			if (pendingStrongsAndMorphTags != null) {
				for (int i = 0; i < pendingStrongsAndMorphTags.size(); i++) {
					write(HTML.SPACE); // separator between adjacent tags and words
					write(pendingStrongsAndMorphTags.get(i));
				}
				write(HTML.SPACE); // separator between adjacent tags and words
				pendingStrongsAndMorphTags = null;
			}
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
		if (HEBREW_LANGUAGE_CODE.equals(parameters.getLanguageCode())) {
			s = doHebrewCharacterAdjustments(s);
		}
		write(s);
	}

	/**
	 * Some characters are not handled well in Android 2.2 & 2.3 and need
	 * special processing which for all except Sof Pasuq means removal
	 * 
	 * @param s
	 * @return adjusted string
	 */
	private String doHebrewCharacterAdjustments(String s) {
		// remove Hebrew vowels because i) they confuse bidi and ii) they are
		// not positioned correctly under/over the appropriate letter
		// http://groups.google.com/group/android-contrib/browse_thread/thread/5b6b079f9ec7792a?pli=1
		s = remove(s, HEBREW_VOWELS_AND_CANTILLATIONS);

		// even without vowel points the : at the end of each verse confuses
		// Android's bidi but specifying the char as rtl helps
		s = s.replace(HEBREW_SOF_PASUQ_CHAR, "<span dir='rtl'>"
				+ HEBREW_SOF_PASUQ_CHAR + "</span> ");
		return s;
	}

	private void writeVerse(int verseNo) {
		currentVersePosition = getWriter().getPosition();
		
		// the id is used to 'jump to' the verse using javascript so always
		// need the verse tag with an id
		StringBuilder verseHtml = new StringBuilder();
		if (parameters.isShowVerseNumbers()) {
			verseHtml.append(" <span class='verse' id='").append(verseNo).append("'>").append(verseNo).append("</span>").append(HTML.NBSP);
		} else {
			// we really want an empty span but that is illegal and causes
			// problems such as incorrect verse calculation in Psalms
			// so use something that will hopefully interfere as little as
			// possible - a zero-width-space
			verseHtml.append("<span class='verse' id='").append(verseNo).append("'/>&#x200b;</span>");
		}
		write(verseHtml.toString());
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

	/**
	 * Convert a Strongs lemma into a url E.g. lemmas "strong:H0430",
	 * "strong:H0853 strong:H01254"
	 * 
	 * @return a single char to use as a note ref
	 */
	private List<String> getStrongsAndMorphTags(String strongsLemma,
			String morphology) {
		// there may occasionally be more than on ref so split them into a list
		// of single refs
		List<String> strongsTags = getStrongsTags(strongsLemma);
		List<String> morphTags = getMorphTags(morphology);

		List<String> mergedStrongsAndMorphTags = new ArrayList<String>();

		// each morph tag should relate to a Strongs tag so they should be same
		// length but can't assume that
		// merge the tags into the merge list
		for (int i = 0; i < Math.max(strongsTags.size(), morphTags.size()); i++) {
			StringBuilder merged = new StringBuilder();
			if (i < strongsTags.size()) {
				merged.append(strongsTags.get(i));
			}
			if (i < morphTags.size()) {
				merged.append(morphTags.get(i));
			}
			mergedStrongsAndMorphTags.add(merged.toString());
		}

		// for some reason the generic tags should come last and the order seems
		// always reversed in other systems
		// the second tag (once reversed) seems to relate to a missing word like
		// eth
		Collections.reverse(mergedStrongsAndMorphTags);
		return mergedStrongsAndMorphTags;
	}

	private List<String> getStrongsTags(String strongsLemma) {
		// there may occasionally be more than on ref so split them into a list
		// of single refs
		List<String> strongsTags = new ArrayList<String>();

		if (parameters.isShowStrongs()) {
			String[] refList = strongsLemma.split(" ");
			for (String ref : refList) {
				// ignore if string doesn't start with "strong;"
				if (ref.startsWith(OSISUtil.LEMMA_STRONGS)
						&& ref.length() > OSISUtil.LEMMA_STRONGS.length() + 2) {
					// reduce ref like "strong:H0430" to "H0430"
					ref = ref.substring(OSISUtil.LEMMA_STRONGS.length());

					// select Hebrew or Greek protocol
					String protocol = null;
					if (ref.startsWith("H")) {
						protocol = Constants.HEBREW_DEF_PROTOCOL;
					} else if (ref.startsWith("G")) {
						protocol = Constants.GREEK_DEF_PROTOCOL;
					}

					if (protocol != null) {
						// remove initial G or H
						String noPadRef = ref.substring(1);
						// pad with leading zeros to 5 characters
						String paddedRef = StringUtils
								.leftPad(noPadRef, 5, "0");

						StringBuilder tag = new StringBuilder();
						// create opening tag for Strong's link
						tag.append("<a href='");

						// calculate uri e.g. H:01234
						tag.append(protocol).append(":").append(paddedRef);

						// set css class
						tag.append("' class='strongs'>");

						// descriptive string
						tag.append(noPadRef);

						// link closing tag
						tag.append("</a>");

						strongsTags.add(tag.toString());
					}
				}
			}
		}
		return strongsTags;
	}

	/**
	 * example of strongs and morphology, we just use Robinson: <w
	 * lemma="strong:G652" morph="robinson:N-NSM" src="2">an apostle</w>
	 * 
	 * @param morphology
	 * @return
	 */
	private List<String> getMorphTags(String morphology) {
		// there may occasionally be more than on ref so split them into a list
		// of single refs
		List<String> morphTags = new ArrayList<String>();

		if (parameters.isShowMorphology()) {
			if (StringUtils.isNotEmpty(morphology)) {
				String[] refList = morphology.split(" ");
				for (String ref : refList) {
					// ignore if string doesn't start with "robinson"
					if (ref.startsWith(OSISUtil.MORPH_ROBINSONS)
							&& ref.length() > OSISUtil.MORPH_ROBINSONS.length() + 2) {
						// reduce ref like "robinson:N-NSM" to "N-NSM" for
						// display
						String display = ref.substring(OSISUtil.MORPH_ROBINSONS
								.length());

						StringBuilder tag = new StringBuilder();
						tag.append("<a href='").append(ref).append(
								"' class='morphology'>").append(display)
								.append("</a>");

						morphTags.add(tag.toString());
					}
				}
			}
		}
		return morphTags;
	}

	/**
	 * StringUtils methods only compare with a single char and hence create lots
	 * of temporary Strings This method compares with all chars and just creates
	 * one new string for each original string. This is to minimise memory
	 * overhead & gc.
	 * 
	 * @param str
	 * @param removeChars
	 * @return
	 */
	public static String remove(String str, char[] removeChars) {
		if (StringUtils.isEmpty(str)
				|| !StringUtils.containsAny(str, removeChars)) {
			return str;
		}

		StringBuilder r = new StringBuilder(str.length());
		// for all chars in string
		for (int i = 0; i < str.length(); i++) {
			char strCur = str.charAt(i);

			// compare with all chars to be removed
			boolean matched = false;
			for (int j = 0; j < removeChars.length && !matched; j++) {
				if (removeChars[j] == strCur) {
					matched = true;
				}
			}
			// if current char does not match any in the list then add it to the
			if (!matched) {
				r.append(strCur);
			}
		}
		return r.toString();
	}

	/**
	 * see if an attribute exists and has a value
	 * 
	 * @param attributeName
	 * @param attrs
	 * @return
	 */
	private boolean isAttr(String attributeName, Attributes attrs) {
		String attrValue = attrs.getValue(attributeName);
		return StringUtils.isNotEmpty(attrValue);
	}

	private String getPaddingAtBottom() {
		return StringUtils.repeat(HTML.BR, parameters
				.getNumPaddingBrsAtBottom());
	}

	public List<Note> getNotesList() {
		return noteAndReferenceHandler.getNotesList();
	}
}
