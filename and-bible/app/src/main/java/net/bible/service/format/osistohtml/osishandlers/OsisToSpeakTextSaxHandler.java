package net.bible.service.format.osistohtml.osishandlers;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class OsisToSpeakTextSaxHandler extends OsisToCanonicalTextSaxHandler {

	private boolean sayReferences;
	
	private boolean writingRef;
	
	public OsisToSpeakTextSaxHandler(boolean sayReferences) {
		super();
		this.sayReferences = sayReferences;
	}

	@Override
	public void startElement(String namespaceURI, String sName, String qName, Attributes attrs) {
		String name = getName(sName, qName); // element name

		debug(name, attrs, true);

		if (sayReferences && name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			writeContent(true);
			writingRef = true;
		} else {
			super.startElement(namespaceURI, sName, qName, attrs);
		}
	}

    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    @Override
    public void endElement(String namespaceURI,
            String sName, // simple name
            String qName  // qualified name
            )
    {
		String name = getName(sName, qName);
		debug(name, null, false);
		if (sayReferences && name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			// A space is needed to separate one verse from the next, otherwise the 2 verses butt up against each other
			// which looks bad and confuses TTS
			writingRef = false;
		}

		super.endElement(namespaceURI, sName, qName);
	}

    /** adjust text in prep for speech
     */
	@Override
	protected void write(String s) {
    	// NetText often uses single quote where esv uses double quote and TTS says open single quote e.g. Matt 4
    	// so replace all single quotes with double quotes but only if they are used for quoting text as in e.g. Ps 117
    	// it is tricky to distinguish single quotes from apostrophes and this won't work all the time

    	if (s.contains(" \'") || s.startsWith("\'")) {
    		s = s.replace("\'", "\"");
    	}
    	// Finney Gospel Sermons contains to many '--'s which are pronounced as hyphen hyphen
    	if (s.contains(" --")) {
    		s = s.replace(" --", ";");
    	}
   		
   		// for xxx's TTS says xxx s instead of xxxs so remove possessive apostrophe 
   		s = s.replace("\'s ", "s ");
		
		// say verse rather than colon etc.
		if (writingRef) {
			s = s.replace(":", " verse ").replace("-", " to ");
		}
		
		super.write(s);
	}
}
