package net.bible.service.format.osistohtml.tei;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TEIUtil {
    
    // E.g. <ref target="StrongsHebrew:00411">H411</ref> taken from StrongsHebrew:00428
    public static final String TEI_ELEMENT_REF = "ref";
    public static final String TEI_ATTR_TARGET = "target";

    public static final String TEI_ELEMENT_ORTH = "orth";
    public static final String TEI_ELEMENT_PRON = "pron";
    // the way tag contents are rendered e.g. 'bold'. 'italic'
    public static final String TEI_ATTR_REND = "rend";
}
