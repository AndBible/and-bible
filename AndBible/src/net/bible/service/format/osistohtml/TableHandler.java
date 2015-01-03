package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/**
 * OSIS provides only very rudimentary tables: a table consists of rows, which in turn consist of cells.
 * Formatting and layout is not part of the table markup; it can either be done automatically, as in HTML
 * browsers, or by inserting some signal to the layout engine, such as type attributes or processing instructions.
 * Note that a table can be nested inside another table. Simply start a new table element inside a cell element.
 *  
 * <table>
 *   <row><cell role="label">Tribe </cell><cell role="label">Administrator</cell></row>
 *   <row><cell>Reuben </cell><cell>Eliezer son of Zichri</cell></row>
 *   <row><cell>Simeon </cell><cell>Shephatiah son of Maacah</cell></row>
 * </table>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class TableHandler {

	private HtmlTextWriter writer;
	
	public TableHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "row";
    }

	public void start(Attributes attrs) {
		writer.write("<table>");
	}

	public void end() {
		writer.write("</table>");
	}
}
