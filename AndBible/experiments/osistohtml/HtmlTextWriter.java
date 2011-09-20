package net.bible.service.format.osistohtml;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.xml.sax.SAXException;

public class HtmlTextWriter {

    private Writer writer;
    
    public HtmlTextWriter() {
        writer = new StringWriter();
    }

    public void write(String htmlText) throws SAXException {
		try {
			writer.write(htmlText);
		} catch (IOException e) {
			throw new SAXException("I/O error", e);
		}
    }
}
