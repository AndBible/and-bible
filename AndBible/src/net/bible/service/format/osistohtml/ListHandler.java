package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/**
 * The main content of a list is encoded using the item element.
 * A list element can be used for outlines that sometimes preceed or follow a biblical passage, such as:
 * <list>
 *   <head>Outline</head>
 *   <item><label>I.</label> The Feasts of Xerxes (1:1-2.18)</item>
 *   <item>
 *     <list>
 *       <item><label>A.</label> Vashti Deposed (ch. 1)</item>
 *       <item><label>B.</label> Esther Made Queen (2:1-18)</item>
 *     </list>
 *   </item>
 * </list>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class ListHandler {

	private HtmlTextWriter writer;
	
	public ListHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "list";
    }

	public void start(Attributes attrs) {
		writer.write("<ul>");
	}

	public void end() {
		writer.write("</ul>");
	}
}
