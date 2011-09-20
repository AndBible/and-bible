/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: CustomHandler.java 2090 2011-03-07 04:13:05Z dmsmith $
 */
package net.bible.service.format.osistohtml;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.DataPolice;
import org.crosswire.jsword.passage.Key;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * To convert OSIS SAX events into HTML.
 * 
 */
public class CustomHandler extends DefaultHandler {
    /**
     * Simple ctor
     */
    public CustomHandler(Book book, Key key) {
        stack = new LinkedList<Content>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String uri, String localname, String qname, Attributes attrs) throws SAXException {
        Element ele = null;

        // If we are looking at the root element
        // then the stack is empty
        if (!stack.isEmpty()) {
            Object top = stack.getFirst();

            // If the element and its descendants are to be ignored
            // then there is a null element on the stack
            if (top == null) {
                return;
            }

            // It might be a text element
            if (top instanceof Element) {
                ele = (Element) top;
            }
        }

        Tag t = getTag(localname, qname);

        if (t != null) {
            stack.addFirst(t.processTag(ele, attrs));
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
     */
    @Override
    public void characters(char[] data, int offset, int length) {
        // what we are adding
        String text = new String(data, offset, length);

        if (stack.isEmpty()) {
            stack.addFirst(new Text(text));
            return;
        }

        // What we are adding to
        Content top = stack.getFirst();

        // If the element and its descendants are to be ignored
        // then there is a null element on the stack
        if (top == null) {
            return;
        }

        if (top instanceof Text) {
            ((Text) top).append(text);
            return;
        }

        if (top instanceof Element) {
            Element current = (Element) top;

            int size = current.getContentSize();

            // If the last element in the list is a string then we should add
            // this string on to the end of it rather than add a new list item
            // because (probably as an artifact of the HTML/XSL transform we get
            // a space inserted in the output even when 2 calls to this method
            // split a word.
            if (size > 0) {
                Content last = current.getContent(size - 1);
                if (last instanceof Text) {
                    ((Text) last).append(text);
                    return;
                }
            }
            current.addContent(new Text(text));
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(String uri, String localname, String qname) {
        if (stack.isEmpty()) {
            return;
        }
        // When we are done processing an element we need to remove
        // it from the stack so that nothing more is attached to it.
        Content top = stack.removeFirst();
        if (top instanceof Element) {
            Element finished = (Element) top;
            Tag t = getTag(localname, qname);

            if (t != null) {
                t.processContent(finished);
            }

            // If it was the last element then it was the root element
            // so save it
            if (stack.isEmpty()) {
                rootElement = finished;
            }
        }
    }

    public Element getRootElement() {
        return rootElement;
    }

    private Tag getTag(String localname, String qname) {
        Tag t = TAG_MAP.get(qname);

        // Some of the THML books are broken in that they use uppercase
        // element names, which the spec disallows, but we might as well
        // look out for them
        if (t == null) {
            t = TAG_MAP.get(qname.toLowerCase(Locale.ENGLISH));

            if (t == null) {
                DataPolice.report("Unknown thml element: " + localname + " qname=" + qname);

                // Report on it only once and make sure the content is output.
                t = new AnonymousTag(qname);
                TAG_MAP.put(qname, t);
                return t;
            }

            DataPolice.report("Wrong case used in thml element: " + qname);
        }
        return t;
    }

    /**
     * When the document is parsed, this is the last element popped off the
     * stack.
     */
    private Element rootElement;

    /**
     * The stack of elements that we have created
     */
    private LinkedList<Content> stack;

    /**
     * The known tag types
     */
    private static final Map<String, Tag> TAG_MAP = new HashMap<String, Tag>();

    static {
        Tag[] tags = new Tag[] {
                new VerseTag()
        };
        
        CommonTagState commonTagState = new CommonTagState();
        HtmlTextWriter htmlTextWriter = new HtmlTextWriter();
        
        for (int i = 0; i < tags.length; i++) {
            Tag t = tags[i];
            t.setCommonTagState(commonTagState);
            t.setWriter(htmlTextWriter);
            
            String tagName = t.getTagName();
            TAG_MAP.put(tagName, t);
            
        }
    }

}
