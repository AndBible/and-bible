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
package org.crosswire.jsword.book.filter.thml;

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
 * To convert SAX events into OSIS events.
 * 
 * <p>
 * I used the THML ref page: <a
 * href="http://www.ccel.org/ThML/ThML1.04.htm">http
 * ://www.ccel.org/ThML/ThML1.04.htm</a> to work out what the tags meant.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class CustomHandler extends DefaultHandler {
    /**
     * Simple ctor
     */
    public CustomHandler(Book book, Key key) {
        DataPolice.setBook(book.getBookMetaData());
        DataPolice.setKey(key);
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
    	//MJD START  Changed by MJD to cope with empty qname
        // sometimes qname is empty e.g. on Android 2.1
        String name;
        if (qname!=null && qname.length()>0) {
            name = qname;
        } else {
            name = localname;
        }
        
        Tag t = TAG_MAP.get(name);

        // Some of the THML books are broken in that they use uppercase
        // element names, which the spec disallows, but we might as well
        // look out for them
        if (t == null) {
            t = TAG_MAP.get(name.toLowerCase(Locale.ENGLISH));

            if (t == null) {
                DataPolice.report("Unknown thml element: " + localname + " name=" + name);

                // Report on it only once and make sure the content is output.
                t = new AnonymousTag(name);
                TAG_MAP.put(name, t);
                return t;
            }

            DataPolice.report("Wrong case used in thml element: " + name);
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
        /*
         * ThML is based upon Voyager XHTML and all Voyager elements are
         * allowed. However not all elements make sense.
         */
        Tag[] tags = new Tag[] {
                // The following are defined in Voyager xhtml 4.0
                new ATag(), new AbbrTag(), new AliasTag("acronym", new AbbrTag()),
                new AnonymousTag("address"),
                new SkipTag("applet"),
                new SkipTag("area"),
                new BTag(), new SkipTag("base"),
                new SkipTag("basefont"),
                new IgnoreTag("bdo"),
                new BigTag(), new BlockquoteTag(), new IgnoreTag("body"),
                new BrTag(), new SkipTag("button"),
                new AnonymousTag("caption"),
                new CenterTag(), new AnonymousTag("cite"),
                new AnonymousTag("code"),
                new SkipTag("col"),
                new SkipTag("colgroup"),
                new AliasTag("dd", new LiTag()),
                new AnonymousTag("del"),
                new AnonymousTag("dfn"),
                new DivTag(), new AliasTag("dl", new UlTag()),
                new AliasTag("dt", new LiTag()),
                new AliasTag("em", new ITag()),
                new IgnoreTag("fieldset"),
                new FontTag(), new SkipTag("form"),
                new SkipTag("frame"),
                new SkipTag("frameset"),
                new AliasTag("h1", new HTag(1)),
                new AliasTag("h2", new HTag(2)),
                new AliasTag("h3", new HTag(3)),
                new AliasTag("h4", new HTag(4)),
                new AliasTag("h5", new HTag(5)),
                new AliasTag("h6", new HTag(6)),
                new SkipTag("head"),
                new HrTag(), new IgnoreTag("html"),
                new IgnoreTag("frameset"),
                new ITag(), new SkipTag("iframe"),
                new ImgTag(), new SkipTag("input"),
                new AnonymousTag("ins"),
                new AnonymousTag("kbd"),
                new AnonymousTag("label"),
                new AnonymousTag("legend"),
                new LiTag(), new SkipTag("link"),
                new SkipTag("map"),
                new SkipTag("meta"),
                new SkipTag("noscript"),
                new SkipTag("object"),
                new OlTag(), new SkipTag("optgroup"),
                new SkipTag("option"),
                new PTag(), new SkipTag("param"),
                new IgnoreTag("pre"),
                new QTag(), new RootTag(), new STag(), new AnonymousTag("samp"),
                new SkipTag("script"),
                new SkipTag("select"),
                new SmallTag(), new IgnoreTag("span"),
                new AliasTag("strong", new BTag()),
                new SkipTag("style"),
                new SubTag(), new SupTag(), new SyncTag(), new TableTag(), new IgnoreTag("tbody"),
                new TdTag(), new IgnoreTag("tfoot"),
                new SkipTag("textarea"),
                new SkipTag("title"),
                new IgnoreTag("thead"),
                new ThTag(), new TrTag(), new TtTag(), new UTag(), new UlTag(), new AnonymousTag("var"),

                // ThML adds the following to Voyager
                // Note: hymn.mod is not here nor are additional head&DC
                // elements
                new AnonymousTag("added"),
                new AnonymousTag("attr"),
                new AnonymousTag("argument"),
                new CitationTag(), new AnonymousTag("date"),
                new AnonymousTag("deleted"),
                new AnonymousTag("def"),
                new AliasTag("div1", new DivTag(1)),
                new AliasTag("div2", new DivTag(2)),
                new AliasTag("div3", new DivTag(3)),
                new AliasTag("div4", new DivTag(4)),
                new AliasTag("div5", new DivTag(5)),
                new AliasTag("div6", new DivTag(6)),
                new ForeignTag(), new AnonymousTag("index"),
                new AnonymousTag("insertIndex"),
                new AnonymousTag("glossary"),
                new NoteTag(), new NameTag(), new PbTag(), new AnonymousTag("scripCom"),
                new AnonymousTag("scripContext"),
                new ScripRefTag(), new ScriptureTag(), new TermTag(), new AnonymousTag("unclear"),
                new VerseTag(),
        };
        for (int i = 0; i < tags.length; i++) {
            Tag t = tags[i];
            String tagName = t.getTagName();
            TAG_MAP.put(tagName, t);
        }
    }

}
